package com.publicissapient.ecommerce.controller;

import com.publicissapient.ecommerce.dto.CreateProductInput;
import com.publicissapient.ecommerce.entity.Product;
import com.publicissapient.ecommerce.exception.ResourceNotFoundException;
import com.publicissapient.ecommerce.repository.ProductRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Controller
@Validated
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;

    @QueryMapping
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @QueryMapping
    public Product getProductById(@Argument Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    @MutationMapping
    public Product createProduct(@Argument @Valid CreateProductInput input) {
        Product product = Product.builder()
                .name(input.getName())
                .description(input.getDescription())
                .price(input.getPrice())
                .stockQuantity(input.getStockQuantity())
                .build();
        return productRepository.save(product);
    }
}
