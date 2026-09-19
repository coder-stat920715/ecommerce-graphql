package com.publicissapient.ecommerce.exception;

/**
 * Thrown when a requested domain entity (User, Product, Order...) cannot be found.
 * Mapped to GraphQL ErrorType.NOT_FOUND by GlobalExceptionResolver.
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final Object identifier;

    public ResourceNotFoundException(String resourceType, Object identifier) {
        super(resourceType + " not found with id: " + identifier);
        this.resourceType = resourceType;
        this.identifier = identifier;
    }

    public String getResourceType() {
        return resourceType;
    }

    public Object getIdentifier() {
        return identifier;
    }
}
