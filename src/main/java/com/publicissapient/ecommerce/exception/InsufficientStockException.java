package com.publicissapient.ecommerce.exception;

/**
 * Thrown when an order requests more units of a product than are currently in stock.
 * Mapped to GraphQL ErrorType.BAD_REQUEST by GlobalExceptionResolver.
 */
public class InsufficientStockException extends RuntimeException {

    private final Long productId;
    private final int requestedQuantity;
    private final int availableQuantity;

    public InsufficientStockException(Long productId, int requestedQuantity, int availableQuantity) {
        super(String.format(
                "Insufficient stock for product %d: requested %d but only %d available",
                productId, requestedQuantity, availableQuantity));
        this.productId = productId;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }

    public Long getProductId() {
        return productId;
    }

    public int getRequestedQuantity() {
        return requestedQuantity;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }
}
