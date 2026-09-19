package com.publicissapient.ecommerce.repository;

import com.publicissapient.ecommerce.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // Bulk fetch used by the OrderItem -> Product @BatchMapping DataLoader.
    List<Product> findByIdIn(List<Long> ids);
}
