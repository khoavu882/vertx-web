package com.github.kaivu.vertxweb.web.validation;

import io.vertx.core.json.JsonObject;

@FunctionalInterface
public interface ValidationRule {
    ValidationResult validate(JsonObject data);

    static ValidationRule required(String field) {
        return data -> {
            if (data == null
                    || !data.containsKey(field)
                    || data.getValue(field) == null
                    || data.getString(field, "").trim().isEmpty()) {
                return ValidationResult.invalid("Field '" + field + "' is required");
            }
            return ValidationResult.valid();
        };
    }

    static ValidationRule email(String field) {
        return data -> {
            String email = data.getString(field);
            if (email != null && !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                return ValidationResult.invalid("Field '" + field + "' must be a valid email address");
            }
            return ValidationResult.valid();
        };
    }

    static ValidationRule minLength(String field, int minLength) {
        return data -> {
            String value = data.getString(field);
            if (value != null && value.length() < minLength) {
                return ValidationResult.invalid(
                        "Field '" + field + "' must be at least " + minLength + " characters long");
            }
            return ValidationResult.valid();
        };
    }

    static ValidationRule maxLength(String field, int maxLength) {
        return data -> {
            String value = data.getString(field);
            if (value != null && value.length() > maxLength) {
                return ValidationResult.invalid(
                        "Field '" + field + "' must be at most " + maxLength + " characters long");
            }
            return ValidationResult.valid();
        };
    }

    static ValidationRule positiveNumber(String field) {
        return data -> {
            Double value = data.getDouble(field);
            if (value != null && value <= 0) {
                return ValidationResult.invalid("Field '" + field + "' must be a positive number");
            }
            return ValidationResult.valid();
        };
    }

    static ValidationRule integerRange(String field, int min, int max) {
        return data -> {
            Integer value = data.getInteger(field);
            if (value != null && (value < min || value > max)) {
                return ValidationResult.invalid("Field '" + field + "' must be between " + min + " and " + max);
            }
            return ValidationResult.valid();
        };
    }
}
