package com.publicissapient.ecommerce.exception;

/**
 * Thrown when an illegal order-status transition is attempted
 * (e.g. moving a CANCELLED order to SHIPPED).
 * Mapped to GraphQL ErrorType.BAD_REQUEST by GlobalExceptionResolver.
 */
public class InvalidOrderStateException extends RuntimeException {
    public InvalidOrderStateException(String message) {
        super(message);
    }
}
