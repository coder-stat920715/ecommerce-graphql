package com.publicissapient.ecommerce.controller;

import com.publicissapient.ecommerce.dto.CreateUserInput;
import com.publicissapient.ecommerce.entity.Order;
import com.publicissapient.ecommerce.entity.User;
import com.publicissapient.ecommerce.exception.ResourceNotFoundException;
import com.publicissapient.ecommerce.repository.OrderRepository;
import com.publicissapient.ecommerce.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Controller
@Validated
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    // -----------------------------------------------------------------
    // QUERIES
    // -----------------------------------------------------------------

    @QueryMapping
    public User getUserById(@Argument Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    @QueryMapping
    public List<User> getAllUsers() {
        // NOTE: If a client requests `orders` on every User here, WITHOUT the
        // @BatchMapping below, Spring GraphQL would invoke a per-user resolver,
        // firing one "SELECT * FROM orders WHERE user_id = ?" per user = the N+1
        // problem. The @BatchMapping method underneath collapses that into ONE
        // "SELECT * FROM orders WHERE user_id IN (...)" query regardless of how
        // many users are returned.
        return userRepository.findAll();
    }

    @MutationMapping
    public User createUser(@Argument @Valid CreateUserInput input) {
        User user = User.builder()
                .name(input.getName())
                .email(input.getEmail())
                .build();
        return userRepository.save(user);
    }

    // -----------------------------------------------------------------
    // BATCH MAPPING (DataLoader) — fixes N+1 for User.orders
    // -----------------------------------------------------------------

    /**
     * Spring GraphQL automatically registers this as a DataLoader for the
     * "User.orders" field. Regardless of how many User objects are in the
     * current query result, all their `orders` are resolved with a single
     * bulk query: SELECT * FROM orders WHERE user_id IN (id1, id2, ...).
     *
     * The Map returned MUST be keyed by the exact User instances passed in.
     */
    @BatchMapping(typeName = "User", field = "orders")
    public Map<User, List<Order>> orders(List<User> users) {
        List<Long> userIds = users.stream().map(User::getId).collect(Collectors.toList());

        log.debug("Batch loading orders for {} users in a single query: {}", users.size(), userIds);

        List<Order> allOrders = orderRepository.findByUserIdIn(userIds);

        Map<Long, List<Order>> ordersByUserId = allOrders.stream()
                .collect(Collectors.groupingBy(o -> o.getUser().getId()));

        return users.stream().collect(Collectors.toMap(
                Function.identity(),
                user -> ordersByUserId.getOrDefault(user.getId(), List.of())
        ));
    }
}
