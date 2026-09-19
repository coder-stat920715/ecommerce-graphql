package com.publicissapient.ecommerce.repository;

import com.publicissapient.ecommerce.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    // Bulk fetch used by the Order -> Items @BatchMapping DataLoader.
    List<OrderItem> findByOrderIdIn(List<Long> orderIds);
}
