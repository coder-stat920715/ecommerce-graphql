//package com.publicissapient.ecommerce;
//
//import com.publicissapient.ecommerce.controller.ProductController;
//import com.publicissapient.ecommerce.entity.Product;
//import com.publicissapient.ecommerce.exception.GlobalExceptionResolver;
//import com.publicissapient.ecommerce.repository.ProductRepository;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.graphql.test.tester.GraphQlTester;
//import org.springframework.graphql.test.tester.GraphQlTester.Response;
//import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest;
//
//import java.math.BigDecimal;
//import java.util.List;
//import java.util.Optional;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.ArgumentMatchers.anyLong;
//import static org.mockito.Mockito.when;
//
///**
// * Slice test: loads ONLY the GraphQL machinery + ProductController, with the
// * repository layer mocked out. This is the fast, isolated counterpart to the
// * full-stack HttpGraphQlTester tests in OrderGraphQlIntegrationTests.
// */
//@GraphQlTest({ProductController.class, GlobalExceptionResolver.class})
//class ProductControllerGraphQlSliceTests {
//
//    @Autowired
//    private GraphQlTester graphQlTester;
//
//    @MockBean
//    private ProductRepository productRepository;
//
//    @Test
//    void getAllProducts_returnsMockedCatalog() {
//        Product laptop = Product.builder()
//                .id(1L).name("Laptop").description("15-inch laptop")
//                .price(new BigDecimal("999.99")).stockQuantity(10)
//                .build();
//
//        when(productRepository.findAll()).thenReturn(List.of(laptop));
//
//        graphQlTester.document("""
//                        query { getAllProducts { id name price stockQuantity } }
//                        """)
//                .execute()
//                .path("getAllProducts[0].name").entity(String.class).isEqualTo("Laptop")
//                .path("getAllProducts[0].price").entity(BigDecimal.class).isEqualTo(new BigDecimal("999.99"));
//    }
//
//    @Test
//    void getProductById_notFound_mapsToNotFoundErrorType() {
//        when(productRepository.findById(anyLong())).thenReturn(Optional.empty());
//
//        Response response = graphQlTester.document("""
//                        query { getProductById(id: 42) { id name } }
//                        """)
//                .execute();
//
//        response.errors().satisfy(errors -> {
//            assertThat(errors).hasSize(1);
//            assertThat(errors.get(0).getErrorType().toString()).isEqualTo("NOT_FOUND");
//        });
//    }
//}
