package com.github.kaivu.vertxweb.web.validation;

import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Validator {
    private final List<ValidationRule> rules;

    private Validator(List<ValidationRule> rules) {
        this.rules = rules;
    }

    public static Validator of(ValidationRule... rules) {
        return new Validator(Arrays.asList(rules));
    }

    public ValidationResult validate(JsonObject data) {
        List<String> errors = new ArrayList<>();

        for (ValidationRule rule : rules) {
            ValidationResult result = rule.validate(data);
            if (!result.isValid()) {
                errors.addAll(result.getErrors());
            }
        }

        return errors.isEmpty() ? ValidationResult.valid() : ValidationResult.invalid(errors);
    }

    // Common validators for different entities
    public static class Users {
        public static final Validator CREATE = Validator.of(
                ValidationRule.required("name"),
                ValidationRule.required("email"),
                ValidationRule.minLength("name", 2),
                ValidationRule.maxLength("name", 50),
                ValidationRule.email("email"));

        public static final Validator UPDATE = Validator.of(
                ValidationRule.minLength("name", 2),
                ValidationRule.maxLength("name", 50),
                ValidationRule.email("email"));
    }

    public static class Products {
        public static final Validator CREATE = Validator.of(
                ValidationRule.required("name"),
                ValidationRule.required("category"),
                ValidationRule.required("price"),
                ValidationRule.minLength("name", 2),
                ValidationRule.maxLength("name", 100),
                ValidationRule.minLength("category", 2),
                ValidationRule.maxLength("category", 50),
                ValidationRule.positiveNumber("price"),
                ValidationRule.integerRange("quantity", 0, 10000));

        public static final Validator UPDATE = Validator.of(
                ValidationRule.minLength("name", 2),
                ValidationRule.maxLength("name", 100),
                ValidationRule.minLength("category", 2),
                ValidationRule.maxLength("category", 50),
                ValidationRule.positiveNumber("price"),
                ValidationRule.integerRange("quantity", 0, 10000));

        public static final Validator STOCK_UPDATE =
                Validator.of(ValidationRule.required("quantity"), ValidationRule.integerRange("quantity", 0, 10000));
    }
}
