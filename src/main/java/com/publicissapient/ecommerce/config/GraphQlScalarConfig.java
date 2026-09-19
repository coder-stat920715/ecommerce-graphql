package com.publicissapient.ecommerce.config;

import graphql.scalars.ExtendedScalars;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * Registers custom scalar types declared in schema.graphqls ("scalar BigDecimal",
 * "scalar DateTime") with concrete implementations from graphql-java-extended-scalars.
 */
@Configuration
public class GraphQlScalarConfig {

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        // ExtendedScalars.GraphQLBigDecimal is already registered under the name "BigDecimal"
        // and ExtendedScalars.DateTime under "DateTime", matching the scalar declarations
        // in schema.graphqls, so we register them as-is.
        return wiringBuilder -> wiringBuilder
                .scalar(ExtendedScalars.GraphQLBigDecimal)
                .scalar(ExtendedScalars.DateTime);
    }
}
