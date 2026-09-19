# Testing Guide — Ecommerce GraphQL Order Management

This guide walks through every way to test the application, in order of
increasing depth: starting the app, importing and running the Postman
collection, testing subscriptions (which Postman can't fully script),
inspecting the database, and running the automated JUnit suite.

Files referenced:
- `ecommerce-graphql.postman_collection.json`
- `ecommerce-graphql.postman_environment.json`

---

## 1. Start the application

```bash
cd ecommerce-graphql
mvn spring-boot:run
```

Wait for a log line like:
```
Started EcommerceGraphqlApplication in X.XXX seconds
```

Confirm it's up:
```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"{ getAllProducts { id name } }"}'
```
You should get back a JSON body with 6 products. If you get `Connection
refused`, the app isn't running yet or is on a different port — check the
console log for the actual port.

**Endpoints to remember:**
| Purpose | URL |
|---|---|
| GraphQL HTTP endpoint | `POST http://localhost:8080/graphql` |
| GraphiQL UI | `http://localhost:8080/graphiql` |
| GraphQL WebSocket (subscriptions) | `ws://localhost:8080/graphql` |
| H2 database console | `http://localhost:8080/h2-console` |

---

## 2. Import into Postman

1. Open Postman → **Import** (top-left).
2. Drag in both files, or select them:
   - `ecommerce-graphql.postman_collection.json`
   - `ecommerce-graphql.postman_environment.json`
3. Postman creates a collection called **"Ecommerce GraphQL - Order
   Management"** and an environment called **"Ecommerce GraphQL - Local"**.
4. In the top-right environment dropdown, select **"Ecommerce GraphQL -
   Local"**. This sets `{{baseUrl}}` to `http://localhost:8080` — change it
   there if your app runs on a different host/port.

### What's inside the collection

```
Ecommerce GraphQL - Order Management/
├── 1. Queries
│   ├── Q1 - Get User By Id (deep nested)
│   ├── Q2 - Get All Users With Orders (N+1 demonstration target)
│   ├── Q3 - Get Orders By Status (PENDING)
│   ├── Q4 - Get All Products (catalog)
│   ├── Q5 - Get Product By Id
│   ├── Q6 - Get Order By Id
│   └── Q7 (Negative) - Get User By Unknown Id -> expect NOT_FOUND error
├── 2. Mutations
│   ├── M1 - Create Order (happy path)
│   ├── M2 (Negative) - Create Order With Insufficient Stock -> BAD_REQUEST
│   ├── M3 (Negative) - Create Order For Unknown User -> NOT_FOUND
│   ├── M4 - Update Order Status (valid transition)
│   ├── M5 (Negative) - Update Order Status Illegal Transition -> BAD_REQUEST
│   ├── M6 - Create User
│   ├── M7 - Create Product
│   └── M8 (Negative) - Create Order With Invalid Quantity -> BAD_REQUEST
└── 3. Admin / Console
    └── H2 Console (open in browser)
```

Every request uses Postman's **GraphQL body mode** (query + variables shown
separately in the Body tab), and every request already has a **Tests**
script attached — the assertions run automatically and show green/red in
the **Test Results** tab after you hit Send.

---

## 3. Run the requests, in order

### Step 3.1 — Run the Queries folder
Right-click **"1. Queries"** → **Run folder** (or run requests one by one
with Send). Expected results:

| Request | Expected outcome |
|---|---|
| Q1 | `data.getUserById.name` = `"Alice Johnson"`, with nested `orders → items → product` all populated |
| Q2 | Array of 4+ users, each with an `orders` array (possibly empty for users with no orders) |
| Q3 | Only orders with `status: "PENDING"` |
| Q4 | 6+ products |
| Q5 | Single product with `id: "1"` |
| Q6 | Order 1 with nested `user` and `items` |
| Q7 | **This one is supposed to fail** — check the response body, not just the status code: HTTP status is still `200`, but the JSON has an `errors` array with `extensions.errorCode: "NOT_FOUND"` |

For every request, open the **Console** (bottom-left "Postman Console") to
see the raw request/response if a test fails — this is the fastest way to
see the exact GraphQL error payload.

### Step 3.2 — Run the Mutations folder
Run **"2. Mutations"** top to bottom. A few things to watch for:

- **M1** creates a real order in the database (decrements stock for
  products 1 and 4). Its `id` is captured into the collection variable
  `lastCreatedOrderId` automatically (see the Tests script) so you can
  chain further requests to it if you duplicate/extend the collection.
- **M2, M3, M8 are intentionally invalid requests.** Passing tests here
  means the error *was correctly produced and correctly classified* —
  not that the app is broken. Read each test name carefully.
- **M4** transitions seeded order `3` (`PENDING`) to `CONFIRMED`. If you
  run the whole folder more than once, this mutation will fail the second
  time because the order is no longer `PENDING` — that's expected. Re-seed
  by restarting the app (H2 is in-memory and resets on every restart) if
  you want a clean run.
- **M5** deliberately tries to move seeded order `1` (`DELIVERED`, a
  terminal state) back to `PENDING`, and expects a `BAD_REQUEST` error.

### Step 3.3 — Re-run to see state changes
Go back to **Q3 (Get Orders By Status: PENDING)** and re-run it after
running the mutations — order `3` should have disappeared from the PENDING
list (it's now CONFIRMED), and order `4`'s status should reflect whatever
you did to it.

---

## 4. Watch the N+1 → single-query fix live

This is the most interview-relevant test. You need the **server console
log** open (SQL logging is enabled via `logging.level.org.hibernate.SQL:
DEBUG`), plus Postman or GraphiQL.

1. Clear/scroll your server console.
2. Send **Q1** (single user, deep nested) via Postman. Count the SQL
   `select` statements printed — you should see a small, fixed number
   (one for the user, one bulk `orders` query, one bulk `order_items`
   query, one bulk `products` query) — **not** one query per nested
   object.
3. Clear the console again. Send **Q2** (`getAllUsers { orders {...} }`).
   Even though there are 4 users, you should see exactly **one** extra
   query of the shape:
   ```sql
   select ... from orders where user_id in (?, ?, ?, ?)
   ```
   That's the `@BatchMapping` DataLoader collapsing what would otherwise
   be 4 separate `WHERE user_id = ?` queries into 1.
4. **To prove the N+1 problem actually exists without batching** (optional,
   more advanced demo): open
   `src/main/java/.../controller/UserController.java`, comment out the
   `@BatchMapping(typeName = "User", field = "orders")` annotation on the
   `orders(...)` method, and replace it with a naive per-object resolver:
   ```java
   @SchemaMapping(typeName = "User", field = "orders")
   public List<Order> ordersNaive(User user) {
       return orderRepository.findByUserIdIn(List.of(user.getId()));
   }
   ```
   Restart the app, re-run Q2, and count queries again — you'll now see
   one query **per user** instead of one query total. Revert the change
   afterward.

---

## 5. Test Subscriptions

Postman's GraphQL body mode does **not** execute subscriptions over
WebSocket the way it executes queries/mutations over HTTP, so subscriptions
are intentionally left out of the importable collection. Use one of these
instead:

### Option A — GraphiQL (easiest, no extra tooling)
1. Open two browser tabs at `http://localhost:8080/graphiql`.
2. **Tab 1** — run:
   ```graphql
   subscription WatchOrder {
     onOrderStatusUpdated(orderId: 4) {
       id
       status
       totalAmount
     }
   }
   ```
   It will sit there "listening" (spinner / no response yet — this is
   correct, it's an open stream).
3. **Tab 2** — run:
   ```graphql
   mutation ShipOrder4 {
     updateOrderStatus(orderId: 4, status: SHIPPED) {
       id
       status
     }
   }
   ```
4. Switch back to **Tab 1** — within a second you should see a new result
   pushed into the subscription pane showing `status: "SHIPPED"`, with no
   need to re-run anything. This confirms the reactive `Flux` pipeline
   (`Sinks.Many` → filtered `Flux` → WebSocket transport) is working.
5. Try the global feed the same way with:
   ```graphql
   subscription WatchAllOrders {
     onAnyOrderStatusUpdated {
       id
       status
       user { name }
     }
   }
   ```
   Any `updateOrderStatus` mutation from any order will show up here.

### Option B — `wscat` from the terminal (shows the raw `graphql-ws` protocol)
```bash
npm install -g wscat
wscat -c ws://localhost:8080/graphql -s graphql-transport-ws
```
Then send these frames one at a time (this is the `graphql-transport-ws`
sub-protocol Spring GraphQL implements):
```json
{"type":"connection_init"}
```
Wait for `{"type":"connection_ack"}`, then:
```json
{"id":"1","type":"subscribe","payload":{"query":"subscription { onOrderStatusUpdated(orderId: 4) { id status } }"}}
```
Trigger a status change from another terminal/Postman/GraphiQL tab:
```bash
curl -s -X POST http://localhost:8080/graphql -H "Content-Type: application/json" \
  -d '{"query":"mutation { updateOrderStatus(orderId: 4, status: SHIPPED) { id status } }"}'
```
You should see a `{"type":"next", "id":"1", "payload":{"data":{"onOrderStatusUpdated":{"id":"4","status":"SHIPPED"}}}}` frame appear in the `wscat` session.

### Option C — Postman (newer desktop app versions only)
Recent Postman desktop versions support a **"WebSocket Request"** type
(separate from a regular HTTP request, and not exportable in the same
`.postman_collection.json` schema used here). If your Postman version has
it: **New → WebSocket Request**, connect to `ws://localhost:8080/graphql`,
and manually send the same `connection_init` / `subscribe` frames shown in
Option B. If you don't see this option in your Postman version, use Option
A or B instead.

---

## 6. Inspect the database directly (H2 console)

1. Open `http://localhost:8080/h2-console` in a browser (not Postman — it's
   a stateful HTML login form, not a JSON API).
2. Fill in:
   - **JDBC URL:** `jdbc:h2:mem:ecommercedb`
   - **User Name:** `sa`
   - **Password:** *(leave empty)*
3. Click **Connect**.
4. Run some sanity checks:
   ```sql
   SELECT * FROM users;
   SELECT * FROM products;
   SELECT * FROM orders;
   SELECT * FROM order_items;
   ```
5. After running **M1** (Create Order) from Postman, re-run
   `SELECT * FROM products WHERE id IN (1, 4);` here — `stock_quantity`
   for both should have decreased by the quantities you ordered,
   confirming the stock-decrement logic in `OrderService.createOrder`
   actually persisted.

---

## 7. Run the automated JUnit test suite

```bash
mvn test
```

This runs two test classes:

- **`OrderGraphQlIntegrationTests`** (`@SpringBootTest` + full HTTP server
  + `HttpGraphQlTester`): exercises real queries, mutations, error mapping,
  and a live subscription assertion using `StepVerifier`, against the
  entire boot context (real H2 DB, real seed data, real WebSocket
  transport).
- **`ProductControllerGraphQlSliceTests`** (`@GraphQlTest` slice, mocked
  `ProductRepository`): a fast, isolated test of just the
  `ProductController` + GraphQL error-mapping wiring, without booting JPA
  or a real server.

Expected output ends with:
```
Tests run: X, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

If a test fails, re-run with `mvn test -Dtest=ClassName#methodName` to
isolate it, and check the console log — Hibernate SQL logging is on, so
you'll usually see exactly which query ran (or didn't).

---

## 8. Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| Postman requests time out / connection refused | App isn't running, or running on a different port — check `mvn spring-boot:run` console output |
| A "happy path" request unexpectedly returns `errors` | Re-run `mvn spring-boot:run` to reset the in-memory H2 database and seed data to a clean state — mutations from a previous run persist until restart |
| M4/M5 fail on a second run | Expected — order status is now different from the seeded state; restart the app to reset |
| Subscription in GraphiQL never fires | Confirm you're running the mutation for the **same `orderId`** the subscription is filtering on, and that both browser tabs are hitting the same running instance |
| `wscat` can't connect | Confirm `-s graphql-transport-ws` sub-protocol flag is included — Spring GraphQL rejects connections without it |
| H2 console "Database not found" | Double-check the JDBC URL is exactly `jdbc:h2:mem:ecommercedb` (must match `application.yml`) and that the app is still running (in-memory DB dies when the app stops) |
