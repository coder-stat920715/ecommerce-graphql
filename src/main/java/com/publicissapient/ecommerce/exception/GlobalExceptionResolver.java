package com.publicissapient.ecommerce.exception;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralized mapping of domain / validation exceptions thrown from
 * DataFetchers (@QueryMapping, @MutationMapping, @BatchMapping methods)
 * into well-typed GraphQL errors with useful "extensions" for API clients.
 *
 * Any exception NOT explicitly handled here is masked by Spring GraphQL's
 * default resolver and returned to the client as a generic INTERNAL_ERROR,
 * so internal details are never leaked.
 */
@Component
public class GlobalExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {

        if (ex instanceof ResourceNotFoundException notFound) {
            return buildError(ErrorType.NOT_FOUND, notFound.getMessage(), env, Map.of(
                    "resourceType", notFound.getResourceType(),
                    "identifier", String.valueOf(notFound.getIdentifier())
            ));
        }

        if (ex instanceof InsufficientStockException stockEx) {
            return buildError(ErrorType.BAD_REQUEST, stockEx.getMessage(), env, Map.of(
                    "productId", String.valueOf(stockEx.getProductId()),
                    "requestedQuantity", stockEx.getRequestedQuantity(),
                    "availableQuantity", stockEx.getAvailableQuantity()
            ));
        }

        if (ex instanceof InvalidOrderStateException stateEx) {
            return buildError(ErrorType.BAD_REQUEST, stateEx.getMessage(), env, Map.of());
        }

        if (ex instanceof MethodArgumentNotValidException validationEx) {
            String details = validationEx.getBindingResult().getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining("; "));
            return buildError(ErrorType.BAD_REQUEST, "Validation failed: " + details, env, Map.of(
                    "fieldErrors", validationEx.getBindingResult().getFieldErrors().stream()
                            .collect(Collectors.toMap(FieldError::getField,
                                    fe -> String.valueOf(fe.getDefaultMessage()),
                                    (a, b) -> a))
            ));
        }

        if (ex instanceof jakarta.validation.ConstraintViolationException cve) {
            String details = cve.getConstraintViolations().stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining("; "));
            return buildError(ErrorType.BAD_REQUEST, "Validation failed: " + details, env, Map.of());
        }

        if (ex instanceof IllegalArgumentException iae) {
            return buildError(ErrorType.BAD_REQUEST, iae.getMessage(), env, Map.of());
        }

        // Unrecognized exception -> let Spring GraphQL mask it as INTERNAL_ERROR.
        return null;
    }

    private GraphQLError buildError(ErrorType type, String message, DataFetchingEnvironment env,
                                     Map<String, Object> extraExtensions) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("errorCode", type.name());
        extensions.put("timestamp", Instant.now().toString());
        extensions.put("classification", type.name());
        extensions.putAll(extraExtensions);

        return GraphqlErrorBuilder.newError(env)
                .message(message)
                .errorType(type)
                .extensions(extensions)
                .build();
    }
}
