package com.publicissapient.ecommerce.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductInput {

    @NotBlank(message = "name is required")
    private String name;

    private String description;

    @NotNull(message = "price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "price must be greater than 0")
    private BigDecimal price;

    @NotNull(message = "stockQuantity is required")
    @Min(value = 0, message = "stockQuantity cannot be negative")
    private Integer stockQuantity;
}
