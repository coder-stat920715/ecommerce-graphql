package com.publicissapient.ecommerce.repository;

import com.publicissapient.ecommerce.entity.Order;
import com.publicissapient.ecommerce.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByStatus(OrderStatus status);

    // Bulk fetch used by the User -> Orders @BatchMapping DataLoader.
    // Underscore notation explicitly traverses the nested "user.id" path,
    // since Order no longer has a flat "userId" JPA-mapped attribute
    // (only a @Transient convenience getter) — without the underscore,
    // Spring Data's query derivation cannot resolve "UserId" as a single
    // attribute and throws PathElementException at startup.
    List<Order> findByUser_IdIn(List<Long> userIds);
}
