package com.publicissapient.ecommerce.repository;

import com.publicissapient.ecommerce.entity.Order;
import com.publicissapient.ecommerce.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByStatus(OrderStatus status);

    // Bulk fetch used by the User -> Orders @BatchMapping DataLoader.
    // A single "WHERE user_id IN (...)" query replaces N individual queries.
    List<Order> findByUserIdIn(List<Long> userIds);
}
