package com.github.kaivu.vertxweb.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.config.ConfigProvider;
import com.github.kaivu.vertxweb.constants.HttpStatusCodes;
import com.github.kaivu.vertxweb.constants.JsonKeys;
import com.github.kaivu.vertxweb.domain.Product;
import com.github.kaivu.vertxweb.observability.metrics.NoopMetricsFacade;
import com.github.kaivu.vertxweb.patterns.CircuitBreakerRegistry;
import com.github.kaivu.vertxweb.repositories.ProductRepository;
import com.github.kaivu.vertxweb.repositories.exception.RepositoryNotFoundException;
import com.github.kaivu.vertxweb.web.exceptions.ServiceException;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ProductServiceTest {
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(2);

    @Test
    void getProductByIdShouldFailWhenIdIsBlank() throws Exception {
        try (TestFixture fixture = TestFixture.start()) {
            ServiceException failure =
                    assertThrows(ServiceException.class, () -> await(fixture.productService.getProductById(" ")));
            assertEquals(HttpStatusCodes.BAD_REQUEST, failure.getStatusCode());
            assertTrue(failure.getMessage().contains("must not be empty"));
        }
    }

    @Test
    void getProductByIdShouldReturnNotFoundForUnknownId() throws Exception {
        try (TestFixture fixture = TestFixture.start()) {
            ServiceException failure =
                    assertThrows(ServiceException.class, () -> await(fixture.productService.getProductById("999")));
            assertEquals(HttpStatusCodes.NOT_FOUND, failure.getStatusCode());
            assertTrue(failure.getMessage().contains("Product not found"));
        }
    }

    @Test
    void getAllProductsShouldReturnListPayload() throws Exception {
        try (TestFixture fixture = TestFixture.start()) {
            JsonObject result = await(fixture.productService.getAllProducts());

            assertEquals(2, result.getInteger("total"));
            assertEquals(2, result.getJsonArray("products").size());
            assertTrue(result.containsKey(JsonKeys.TIMESTAMP));
        }
    }

    @Test
    void createProductShouldFailWhenPayloadIsEmpty() throws Exception {
        try (TestFixture fixture = TestFixture.start()) {
            ServiceException failure = assertThrows(
                    ServiceException.class, () -> await(fixture.productService.createProduct(new JsonObject())));
            assertEquals(HttpStatusCodes.BAD_REQUEST, failure.getStatusCode());
            assertTrue(failure.getMessage().contains("must not be empty"));
        }
    }

    @Test
    void createProductShouldValidateRequiredAndNumericFields() throws Exception {
        try (TestFixture fixture = TestFixture.start()) {
            ServiceException missingName = assertThrows(
                    ServiceException.class,
                    () -> await(fixture.productService.createProduct(
                            new JsonObject().put("category", "Electronics").put("price", 10.0))));
            assertEquals(HttpStatusCodes.BAD_REQUEST, missingName.getStatusCode());

            ServiceException missingCategory = assertThrows(
                    ServiceException.class,
                    () -> await(fixture.productService.createProduct(
                            new JsonObject().put("name", "Keyboard").put("price", 10.0))));
            assertEquals(HttpStatusCodes.BAD_REQUEST, missingCategory.getStatusCode());

            ServiceException invalidPrice = assertThrows(
                    ServiceException.class,
                    () -> await(fixture.productService.createProduct(new JsonObject()
                            .put("name", "Keyboard")
                            .put("category", "Electronics")
                            .put("price", 0.0))));
            assertEquals(HttpStatusCodes.BAD_REQUEST, invalidPrice.getStatusCode());
        }
    }

    @Test
    void createProductShouldReturnExpectedPayload() throws Exception {
        try (TestFixture fixture = TestFixture.start()) {
            JsonObject payload = new JsonObject()
                    .put("name", "Laptop")
                    .put("category", "Electronics")
                    .put("price", 1599.0)
                    .put("quantity", 10);

            JsonObject result = await(fixture.productService.createProduct(payload));

            assertEquals("Laptop", result.getString("name"));
            assertEquals("Electronics", result.getString("category"));
            assertEquals(1599.0, result.getDouble("price"));
            assertEquals(10, result.getInteger("quantity"));
            assertTrue(result.getBoolean("inStock"));
            assertTrue(result.containsKey("productId"));
            assertTrue(result.containsKey("createdAt"));
        }
    }

    @Test
    void updateProductStockShouldFailForInvalidInput() throws Exception {
        try (TestFixture fixture = TestFixture.start()) {
            ServiceException blankId = assertThrows(
                    ServiceException.class, () -> await(fixture.productService.updateProductStock(" ", 10)));
            assertEquals(HttpStatusCodes.BAD_REQUEST, blankId.getStatusCode());

            ServiceException negativeQuantity = assertThrows(
                    ServiceException.class, () -> await(fixture.productService.updateProductStock("1", -1)));
            assertEquals(HttpStatusCodes.BAD_REQUEST, negativeQuantity.getStatusCode());
        }
    }

    @Test
    void updateProductStockShouldUpdateQuantityAndInStock() throws Exception {
        try (TestFixture fixture = TestFixture.start()) {
            JsonObject updated = await(fixture.productService.updateProductStock("1", 0));

            assertEquals("1", updated.getString("productId"));
            assertEquals(0, updated.getInteger("quantity"));
            assertFalse(updated.getBoolean("inStock"));
            assertTrue(updated.containsKey("updatedAt"));
        }
    }

    private static <T> T await(Uni<T> uni) {
        return uni.await().atMost(AWAIT_TIMEOUT);
    }

    private static final class TestFixture implements AutoCloseable {
        private final Vertx vertx;
        private final ProductService productService;
        private final Map<String, String> originals;
        private final Set<String> overriddenKeys;

        private TestFixture(
                Vertx vertx, ProductService productService, Map<String, String> originals, Set<String> overriddenKeys) {
            this.vertx = vertx;
            this.productService = productService;
            this.originals = originals;
            this.overriddenKeys = overriddenKeys;
        }

        static TestFixture start() {
            Map<String, String> overrides = Map.ofEntries(
                    Map.entry("app.service.base-delay-ms", "1"),
                    Map.entry("app.service.max-delay-variance-ms", "1"),
                    Map.entry("app.service.product-create-base-delay-ms", "1"),
                    Map.entry("app.service.product-create-max-variance-ms", "1"),
                    Map.entry("app.service.circuit-breaker-timeout-ms", "1000"),
                    Map.entry("app.service.circuit-breaker-failure-threshold", "5"),
                    Map.entry("app.service.circuit-breaker-success-threshold", "3"),
                    Map.entry("app.service.circuit-breaker-reset-timeout-ms", "60000"));
            Map<String, String> originals = captureAndApply(overrides);

            Vertx vertx = Vertx.vertx();
            ApplicationConfig appConfig = ConfigProvider.createConfig();
            CircuitBreakerRegistry circuitBreakerRegistry =
                    new CircuitBreakerRegistry(vertx, appConfig, new NoopMetricsFacade());
            ProductService productService = new ProductService(new InMemoryProductRepository(), circuitBreakerRegistry);
            return new TestFixture(vertx, productService, originals, overrides.keySet());
        }

        @Override
        public void close() throws Exception {
            try {
                vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            } finally {
                restoreProperties(originals, overriddenKeys);
            }
        }

        private static Map<String, String> captureAndApply(Map<String, String> overrides) {
            Map<String, String> originals = new HashMap<>();
            for (Map.Entry<String, String> entry : overrides.entrySet()) {
                String key = entry.getKey();
                originals.put(key, System.getProperty(key));
                System.setProperty(key, entry.getValue());
            }
            return originals;
        }

        private static void restoreProperties(Map<String, String> originals, Set<String> keys) {
            for (String key : keys) {
                String previous = originals.get(key);
                if (previous == null) {
                    System.clearProperty(key);
                } else {
                    System.setProperty(key, previous);
                }
            }
        }
    }

    private static final class InMemoryProductRepository implements ProductRepository {
        @Override
        public Uni<Product> findById(String productId) {
            return switch (productId) {
                case "1" -> Uni.createFrom()
                        .item(new Product("1", "Widget", "Hardware", BigDecimal.valueOf(9.99), 15, true, null));
                case "2" -> Uni.createFrom()
                        .item(new Product("2", "Phone", "Electronics", BigDecimal.valueOf(299.99), 9, true, null));
                default -> Uni.createFrom().failure(new RepositoryNotFoundException("Product not found: " + productId));
            };
        }

        @Override
        public Uni<List<Product>> findAll() {
            return Uni.createFrom()
                    .item(List.of(
                            new Product("1", "Widget", "Hardware", BigDecimal.valueOf(9.99), 15, true, null),
                            new Product("2", "Phone", "Electronics", BigDecimal.valueOf(299.99), 9, true, null)));
        }

        @Override
        public Uni<Product> save(Product product) {
            Product saved = new Product(
                    UUID.randomUUID().toString(),
                    product.name(),
                    product.category(),
                    product.price(),
                    product.quantity(),
                    product.inStock(),
                    LocalDateTime.now());
            return Uni.createFrom().item(saved);
        }

        @Override
        public Uni<Product> updateStock(String productId, int quantity) {
            return findById(productId)
                    .onItem()
                    .transform(p -> new Product(
                            p.productId(), p.name(), p.category(), p.price(), quantity, quantity > 0, p.createdAt()));
        }

        @Override
        public Uni<Boolean> delete(String productId) {
            return findById(productId).onItem().transform(ignored -> true);
        }
    }
}
