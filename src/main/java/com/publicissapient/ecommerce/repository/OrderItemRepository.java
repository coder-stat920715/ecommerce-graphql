package com.publicissapient.ecommerce.repository;

import com.publicissapient.ecommerce.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    // Bulk fetch used by the Order -> Items @BatchMapping DataLoader.
    // Underscore notation traverses the nested "order.id" association path
    // (OrderItem has no flat "orderId" attribute, only the "order" association).
    List<OrderItem> findByOrder_IdIn(List<Long> orderIds);
}
