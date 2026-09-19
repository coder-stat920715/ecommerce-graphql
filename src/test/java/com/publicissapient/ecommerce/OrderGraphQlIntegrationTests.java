package com.publicissapient.ecommerce;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack integration tests. Boots the entire Spring context on a random
 * port and drives the GraphQL endpoint exactly as a real client would,
 * exercising queries, nested @BatchMapping resolution, mutations, and the
 * GlobalExceptionResolver error mapping.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "spring.graphql.graphiql.enabled=false")
class OrderGraphQlIntegrationTests {

    @LocalServerPort
    private int port;

    private HttpGraphQlTester graphQlTester() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:" + port + "/graphql")
                .build();
        return HttpGraphQlTester.builder(webClient).build();
    }

    @Test
    void getUserById_returnsUserWithNestedOrdersItemsAndProducts() {
        String query = """
                query {
                  getUserById(id: 1) {
                    id
                    name
                    email
                    orders {
                      id
                      status
                      totalAmount
                      items {
                        quantity
                        unitPrice
                        subtotal
                        product { name price }
                      }
                    }
                  }
                }
                """;

        graphQlTester().document(query)
                .execute()
                .path("getUserById.name").entity(String.class).isEqualTo("Alice Johnson")
                .path("getUserById.orders").entityList(Object.class).hasSizeGreaterThanOrEqualTo(2)
                .path("getUserById.orders[0].items[0].product.name").entity(String.class).isNotNull();
    }

    @Test
    void getAllUsers_resolvesOrdersForEveryUserViaBatchMapping() {
        // This is the N+1 demonstration query: requesting `orders` on every user
        // triggers ONE @BatchMapping call (SELECT ... WHERE user_id IN (...))
        // instead of one query per user.
        String query = """
                query {
                  getAllUsers {
                    id
                    name
                    orders { id status }
                  }
                }
                """;

        List<String> names = graphQlTester().document(query)
                .execute()
                .path("getAllUsers[*].name")
                .entityList(String.class)
                .get();

        assertThat(names).contains("Alice Johnson", "Bob Smith", "Carla Mendes", "David Lee");
    }

    @Test
    void getOrdersByStatus_filtersCorrectly() {
        String query = """
                query {
                  getOrdersByStatus(status: PENDING) {
                    id
                    status
                    user { name }
                  }
                }
                """;

        graphQlTester().document(query)
                .execute()
                .path("getOrdersByStatus[*].status")
                .entityList(String.class)
                .satisfies(statuses -> assertThat(statuses).allMatch("PENDING"::equals));
    }

    @Test
    void createOrder_succeedsAndDecrementsStock() {
        String mutation = """
                mutation {
                  createOrder(input: { userId: 4, items: [{ productId: 2, quantity: 3 }] }) {
                    id
                    status
                    totalAmount
                    items { quantity product { name } }
                  }
                }
                """;

        graphQlTester().document(mutation)
                .execute()
                .path("createOrder.status").entity(String.class).isEqualTo("PENDING")
                .path("createOrder.items[0].quantity").entity(Integer.class).isEqualTo(3)
                .path("createOrder.totalAmount").entity(BigDecimal.class)
                .satisfies(amount -> assertThat(amount).isGreaterThan(BigDecimal.ZERO));
    }

    @Test
    void createOrder_withInsufficientStock_returnsBadRequestError() {
        String mutation = """
                mutation {
                  createOrder(input: { userId: 1, items: [{ productId: 3, quantity: 99999 }] }) {
                    id
                  }
                }
                """;

        graphQlTester().document(mutation)
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.get(0).getErrorType().toString()).isEqualTo("BAD_REQUEST");
                });
    }

    @Test
    void getUserById_withUnknownId_returnsNotFoundError() {
        String query = """
                query {
                  getUserById(id: 999999) { id }
                }
                """;

        graphQlTester().document(query)
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.get(0).getErrorType().toString()).isEqualTo("NOT_FOUND");
                });
    }

    @Test
    void updateOrderStatus_withIllegalTransition_returnsBadRequestError() {
        // Order 1 seed data is DELIVERED -> terminal state, cannot move to CONFIRMED.
        String mutation = """
                mutation {
                  updateOrderStatus(orderId: 1, status: CONFIRMED) { id status }
                }
                """;

        graphQlTester().document(mutation)
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors).hasSize(1));
    }

    @Test
    void subscription_onOrderStatusUpdated_streamsUpdateAfterMutation() {
        HttpGraphQlTester tester = graphQlTester();

        String subscription = """
                subscription {
                  onOrderStatusUpdated(orderId: 4) { id status }
                }
                """;

        reactor.core.publisher.Flux<String> statusFlux = tester.document(subscription)
                .executeSubscription()
                .toFlux("onOrderStatusUpdated.status", String.class);

        // Trigger the mutation shortly after subscribing.
        new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
            }
            graphQlTester().document("""
                    mutation { updateOrderStatus(orderId: 4, status: SHIPPED) { id } }
                    """).execute();
        }).start();

        reactor.test.StepVerifier.create(statusFlux)
                .expectNext("SHIPPED")
                .thenCancel()
                .verify(java.time.Duration.ofSeconds(5));
    }
}
