package com.github.kaivu.vertxweb.web.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

class ValidatorTest {
    @Test
    void usersCreateValidatorShouldRejectMissingRequiredFields() {
        ValidationResult result = Validator.Users.CREATE.validate(new JsonObject());

        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Field 'name' is required"));
        assertTrue(result.getErrors().contains("Field 'email' is required"));
    }

    @Test
    void usersCreateValidatorShouldRejectInvalidEmailFormat() {
        JsonObject payload = new JsonObject().put("name", "Alice").put("email", "alice-at-example.com");

        ValidationResult result = Validator.Users.CREATE.validate(payload);

        assertFalse(result.isValid());
        assertTrue(result.getAllErrors().contains("must be a valid email address"));
    }

    @Test
    void productsCreateValidatorShouldRejectInvalidPriceAndQuantity() {
        JsonObject payload = new JsonObject()
                .put("name", "Phone")
                .put("category", "Electronics")
                .put("price", -1.0)
                .put("quantity", 20001);

        ValidationResult result = Validator.Products.create(100).validate(payload);

        assertFalse(result.isValid());
        assertTrue(result.getAllErrors().contains("must be a positive number"));
        assertTrue(result.getAllErrors().contains("must be between 0 and 10000"));
    }

    @Test
    void stockUpdateValidatorShouldRequireQuantityField() {
        ValidationResult result = Validator.Products.STOCK_UPDATE.validate(new JsonObject());

        assertFalse(result.isValid());
        assertTrue(result.getAllErrors().contains("Field 'quantity' is required"));
    }

    @Test
    void usersUpdateValidatorShouldAcceptPartialPayload() {
        JsonObject payload = new JsonObject().put("name", "Updated Name");

        ValidationResult result = Validator.Users.UPDATE.validate(payload);

        assertTrue(result.isValid());
    }
}
