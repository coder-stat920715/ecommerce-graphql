package com.publicissapient.ecommerce.controller;

import com.publicissapient.ecommerce.dto.CreateOrderInput;
import com.publicissapient.ecommerce.entity.Order;
import com.publicissapient.ecommerce.entity.OrderItem;
import com.publicissapient.ecommerce.entity.OrderStatus;
import com.publicissapient.ecommerce.entity.Product;
import com.publicissapient.ecommerce.entity.User;
import com.publicissapient.ecommerce.exception.ResourceNotFoundException;
import com.publicissapient.ecommerce.repository.OrderItemRepository;
import com.publicissapient.ecommerce.repository.OrderRepository;
import com.publicissapient.ecommerce.repository.ProductRepository;
import com.publicissapient.ecommerce.repository.UserRepository;
import com.publicissapient.ecommerce.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Controller
@Validated
@RequiredArgsConstructor
public class OrderController {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderService orderService;

    // -----------------------------------------------------------------
    // QUERIES
    // -----------------------------------------------------------------

    @QueryMapping
    public Order getOrderById(@Argument Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    @QueryMapping
    public List<Order> getOrdersByStatus(@Argument OrderStatus status) {
        return orderService.getOrdersByStatus(status);
    }

    // -----------------------------------------------------------------
    // MUTATIONS
    // -----------------------------------------------------------------

    @MutationMapping
    public Order createOrder(@Argument @Valid CreateOrderInput input) {
        return orderService.createOrder(input);
    }

    @MutationMapping
    public Order updateOrderStatus(@Argument Long orderId, @Argument OrderStatus status) {
        return orderService.updateOrderStatus(orderId, status);
    }

    // -----------------------------------------------------------------
    // SUBSCRIPTIONS
    // -----------------------------------------------------------------

    @SubscriptionMapping
    public Flux<Order> onOrderStatusUpdated(@Argument Long orderId) {
        log.info("New subscription opened for orderId={}", orderId);
        return orderService.orderStatusUpdates()
                .filter(order -> order.getId().equals(orderId));
    }

    @SubscriptionMapping
    public Flux<Order> onAnyOrderStatusUpdated() {
        log.info("New subscription opened for ALL order status updates");
        return orderService.orderStatusUpdates();
    }

    // -----------------------------------------------------------------
    // BATCH MAPPING (DataLoader) — fixes N+1 for Order.user, Order.items,
    // and OrderItem.product. Together these turn a deeply nested query like
    //   users { orders { items { product { name } } } }
    // from O(N) + O(N*M) + O(N*M) queries into exactly 3 bulk queries total,
    // no matter how many users/orders/items are returned.
    // -----------------------------------------------------------------

    @BatchMapping(typeName = "Order", field = "user")
    public Map<Order, User> user(List<Order> orders) {
        List<Long> userIds = orders.stream().map(Order::getUserId).distinct().collect(Collectors.toList());

        log.debug("Batch loading {} distinct users for {} orders in a single query", userIds.size(), orders.size());

        Map<Long, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return orders.stream().collect(Collectors.toMap(
                Function.identity(),
                order -> usersById.get(order.getUserId())
        ));
    }

    @BatchMapping(typeName = "Order", field = "items")
    public Map<Order, List<OrderItem>> items(List<Order> orders) {
        List<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toList());

        log.debug("Batch loading order items for {} orders in a single query", orders.size());

        List<OrderItem> allItems = orderItemRepository.findByOrderIdIn(orderIds);

        Map<Long, List<OrderItem>> itemsByOrderId = allItems.stream()
                .collect(Collectors.groupingBy(item -> item.getOrder().getId()));

        return orders.stream().collect(Collectors.toMap(
                Function.identity(),
                order -> itemsByOrderId.getOrDefault(order.getId(), List.of())
        ));
    }

    @BatchMapping(typeName = "OrderItem", field = "product")
    public Map<OrderItem, Product> product(List<OrderItem> items) {
        List<Long> productIds = items.stream().map(OrderItem::getProductId).distinct().collect(Collectors.toList());

        log.debug("Batch loading {} distinct products for {} order items in a single query",
                productIds.size(), items.size());

        Map<Long, Product> productsById = productRepository.findByIdIn(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        return items.stream().collect(Collectors.toMap(
                Function.identity(),
                item -> productsById.get(item.getProductId())
        ));
    }
}
