package com.publicissapient.ecommerce.service;

import com.publicissapient.ecommerce.dto.CreateOrderInput;
import com.publicissapient.ecommerce.dto.OrderItemInput;
import com.publicissapient.ecommerce.entity.Order;
import com.publicissapient.ecommerce.entity.OrderItem;
import com.publicissapient.ecommerce.entity.OrderStatus;
import com.publicissapient.ecommerce.entity.Product;
import com.publicissapient.ecommerce.entity.User;
import com.publicissapient.ecommerce.exception.InsufficientStockException;
import com.publicissapient.ecommerce.exception.InvalidOrderStateException;
import com.publicissapient.ecommerce.exception.ResourceNotFoundException;
import com.publicissapient.ecommerce.repository.OrderRepository;
import com.publicissapient.ecommerce.repository.ProductRepository;
import com.publicissapient.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    // Multicast, replay-free sink: every currently-subscribed client receives new
    // order events. Backpressure strategy BUFFER protects slow subscribers.
    private final Sinks.Many<Order> orderStatusSink = Sinks.many().multicast().onBackpressureBuffer();

    // Legal order-status transitions, enforced by updateOrderStatus().
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);
    static {
        ALLOWED_TRANSITIONS.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.SHIPPED, EnumSet.of(OrderStatus.DELIVERED));
        ALLOWED_TRANSITIONS.put(OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    @Transactional
    public Order createOrder(CreateOrderInput input) {
        User user = userRepository.findById(input.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", input.getUserId()));

        Order order = Order.builder()
                .user(user)
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        BigDecimal total = BigDecimal.ZERO;

        for (OrderItemInput itemInput : input.getItems()) {
            Product product = productRepository.findById(itemInput.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", itemInput.getProductId()));

            if (product.getStockQuantity() < itemInput.getQuantity()) {
                throw new InsufficientStockException(
                        product.getId(), itemInput.getQuantity(), product.getStockQuantity());
            }

            // Decrement stock atomically as part of the same transaction.
            product.setStockQuantity(product.getStockQuantity() - itemInput.getQuantity());
            productRepository.save(product);

            OrderItem item = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(itemInput.getQuantity())
                    .unitPrice(product.getPrice())
                    .build();

            order.getItems().add(item);
            total = total.add(item.getSubtotal());
        }

        order.setTotalAmount(total);
        Order saved = orderRepository.save(order);
        log.info("Created order id={} for userId={} totalAmount={}", saved.getId(), user.getId(), total);
        return saved;
    }

    @Transactional
    public Order updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        OrderStatus currentStatus = order.getStatus();
        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, EnumSet.noneOf(OrderStatus.class));

        if (currentStatus != newStatus && !allowed.contains(newStatus)) {
            throw new InvalidOrderStateException(
                    "Cannot transition order " + orderId + " from " + currentStatus + " to " + newStatus);
        }

        order.setStatus(newStatus);
        Order saved = orderRepository.save(order);

        // Publish to any active subscribers (onOrderStatusUpdated / onAnyOrderStatusUpdated).
        Sinks.EmitResult result = orderStatusSink.tryEmitNext(saved);
        if (result.isFailure()) {
            log.warn("Failed to emit order status update for orderId={}, result={}", orderId, result);
        }

        log.info("Order id={} transitioned {} -> {}", orderId, currentStatus, newStatus);
        return saved;
    }

    /** Stream of ALL order updates, used to implement per-order and global subscriptions. */
    public Flux<Order> orderStatusUpdates() {
        return orderStatusSink.asFlux();
    }

    public List<Order> getOrdersByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }
}
