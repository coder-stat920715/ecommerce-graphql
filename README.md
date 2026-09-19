# E-Commerce Order Management — Spring GraphQL Demo

Production-grade Spring Boot 3 + Spring GraphQL reference project modeling a
`User -> Order -> OrderItem -> Product` domain. Built for demonstrating
schema-first GraphQL design, N+1 resolution with `@BatchMapping`, reactive
subscriptions, and typed error handling.

## Stack
- Java 17, Spring Boot 3.3.4
- `spring-boot-starter-graphql` (schema-first)
- Spring Data JPA + H2 in-memory
- Jakarta Bean Validation
- `graphql-java-extended-scalars` (`BigDecimal`, `DateTime`)
- Reactor `Sinks`/`Flux` for subscriptions

## Running

```bash
mvn spring-boot:run
```

- GraphiQL UI: **http://localhost:8080/graphiql**
- GraphQL HTTP endpoint: `POST http://localhost:8080/graphql`
- GraphQL WebSocket endpoint (subscriptions): `ws://localhost:8080/graphql`
- H2 console: **http://localhost:8080/h2-console**
  (JDBC URL: `jdbc:h2:mem:ecommercedb`, user `sa`, empty password)

Run tests:
```bash
mvn test
```

## Seed data
4 users, 6 products, 5 orders (one of each status) are loaded from `data.sql`
on startup — see that file for exact IDs.

---

## Demonstrating the N+1 problem live

1. Open GraphiQL and enable "tracing"/watch the console log (SQL logging is
   on — `logging.level.org.hibernate.SQL: DEBUG`).
2. Run **Query 2** below (`getAllUsers` with nested `orders`). Because
   `User.orders` is resolved by the `@BatchMapping` method in
   `UserController`, you will see **exactly one** extra
   `SELECT ... FROM orders WHERE user_id IN (...)` in the log, no matter how
   many users are returned.
3. To *prove* what would happen without batching, temporarily rename
   `@BatchMapping(typeName = "User", field = "orders")` to something else
   (or delete it) and add a naive `@SchemaMapping(typeName = "User", field =
   "orders") public List<Order> orders(User user) { return
   orderRepository.findByUserIdIn(List.of(user.getId())); }` — re-run the
   same query and you'll see one query *per user* in the log instead of one
   total. Put the `@BatchMapping` back afterward.

---

## Sample Operations for GraphiQL

### Query 1 — Single user, deep nested graph (User → Orders → Items → Product)

```graphql
query DeepUserGraph {
  getUserById(id: 1) {
    id
    name
    email
    orders {
      id
      status
      totalAmount
      createdAt
      items {
        id
        quantity
        unitPrice
        subtotal
        product {
          id
          name
          price
        }
      }
    }
  }
}
```

### Query 2 — All users with nested orders (N+1 demo target)

```graphql
query AllUsersWithOrders {
  getAllUsers {
    id
    name
    orders {
      id
      status
      totalAmount
    }
  }
}
```

### Query 3 — Orders filtered by status

```graphql
query PendingOrders {
  getOrdersByStatus(status: PENDING) {
    id
    status
    totalAmount
    user {
      name
      email
    }
  }
}
```

### Query 4 — Product catalog

```graphql
query Catalog {
  getAllProducts {
    id
    name
    description
    price
    stockQuantity
  }
}
```

---

### Mutation 1 — Create an order (happy path)

```graphql
mutation PlaceOrder {
  createOrder(input: {
    userId: 2
    items: [
      { productId: 1, quantity: 1 }
      { productId: 4, quantity: 2 }
    ]
  }) {
    id
    status
    totalAmount
    createdAt
    items {
      quantity
      unitPrice
      subtotal
      product { name }
    }
  }
}
```

### Mutation 2 — Create an order that exceeds stock (error-handling demo)

```graphql
mutation OverorderStock {
  createOrder(input: {
    userId: 1
    items: [{ productId: 3, quantity: 999999 }]
  }) {
    id
  }
}
```
Expect a single GraphQL error with `extensions.errorCode = "BAD_REQUEST"` and
`extensions.availableQuantity` / `requestedQuantity` populated.

### Mutation 3 — Reference a non-existent user (NOT_FOUND demo)

```graphql
mutation UnknownUser {
  createOrder(input: { userId: 999999, items: [{ productId: 1, quantity: 1 }] }) {
    id
  }
}
```
Expect `extensions.errorCode = "NOT_FOUND"`.

### Mutation 4 — Update order status (valid transition)

```graphql
mutation ConfirmOrder {
  updateOrderStatus(orderId: 3, status: CONFIRMED) {
    id
    status
  }
}
```

### Mutation 5 — Invalid state transition (BAD_REQUEST demo)

```graphql
mutation IllegalTransition {
  updateOrderStatus(orderId: 1, status: PENDING) {
    id
    status
  }
}
```
Order 1 is seeded as `DELIVERED`, a terminal status — expect a `BAD_REQUEST`
error explaining the illegal transition.

### Mutation 6 — Create a user

```graphql
mutation NewUser {
  createUser(input: { name: "Priya Nair", email: "priya.nair@example.com" }) {
    id
    name
    email
  }
}
```

### Mutation 7 — Create a product

```graphql
mutation NewProduct {
  createProduct(input: {
    name: "Webcam 1080p"
    description: "USB webcam with built-in mic"
    price: 45.00
    stockQuantity: 60
  }) {
    id
    name
    price
  }
}
```

### Mutation 8 — Validation failure demo (negative quantity)

```graphql
mutation InvalidQuantity {
  createOrder(input: { userId: 1, items: [{ productId: 1, quantity: 0 }] }) {
    id
  }
}
```
Expect a `BAD_REQUEST` validation error (`quantity must be at least 1`).

---

### Subscription 1 — Live updates for one order

Open a second GraphiQL tab/session and run:

```graphql
subscription WatchOrder {
  onOrderStatusUpdated(orderId: 4) {
    id
    status
    totalAmount
  }
}
```

Then, in the first tab, run **Mutation 4 / 5 style transitions** for order
`4` (seeded `CONFIRMED`), e.g.:

```graphql
mutation ShipOrder4 {
  updateOrderStatus(orderId: 4, status: SHIPPED) {
    id
    status
  }
}
```

The subscription tab immediately emits the updated `Order` (status:
`SHIPPED`), pushed over the GraphQL WebSocket transport.

### Subscription 2 — Global order-status feed (ops dashboard style)

```graphql
subscription WatchAllOrders {
  onAnyOrderStatusUpdated {
    id
    status
    user { name }
  }
}
```

Trigger any `updateOrderStatus` mutation from another tab to see it stream
in here regardless of `orderId`.

---

## Error-handling summary

| Exception                      | GraphQL `ErrorType` | Extensions payload                                   |
|---------------------------------|----------------------|--------------------------------------------------------|
| `ResourceNotFoundException`     | `NOT_FOUND`          | `resourceType`, `identifier`                           |
| `InsufficientStockException`    | `BAD_REQUEST`        | `productId`, `requestedQuantity`, `availableQuantity`   |
| `InvalidOrderStateException`    | `BAD_REQUEST`        | `errorCode`, `timestamp`                                |
| `MethodArgumentNotValidException` (Jakarta `@Valid`) | `BAD_REQUEST` | `fieldErrors` map                          |
| Anything else                   | `INTERNAL_ERROR` (masked, default Spring GraphQL behavior) | — |

All handled in `GlobalExceptionResolver` (`DataFetcherExceptionResolverAdapter`).
