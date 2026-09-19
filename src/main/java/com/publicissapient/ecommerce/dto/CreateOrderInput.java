package com.publicissapient.ecommerce.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderInput {

    @NotNull(message = "userId is required")
    private Long userId;

    @NotEmpty(message = "an order must contain at least one item")
    @Valid
    private List<OrderItemInput> items;
}
