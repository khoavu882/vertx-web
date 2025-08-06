package com.github.kaivu.vertxweb.web.validation;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

@Getter
public class ValidationResult {
    private final boolean valid;
    private final List<String> errors;

    private ValidationResult(boolean valid, List<String> errors) {
        this.valid = valid;
        this.errors = errors != null ? errors : new ArrayList<>();
    }

    public static ValidationResult valid() {
        return new ValidationResult(true, new ArrayList<>());
    }

    public static ValidationResult invalid(List<String> errors) {
        return new ValidationResult(false, errors);
    }

    public static ValidationResult invalid(String error) {
        List<String> errors = new ArrayList<>();
        errors.add(error);
        return new ValidationResult(false, errors);
    }

    public String getFirstError() {
        return errors.isEmpty() ? null : errors.get(0);
    }

    public String getAllErrors() {
        return String.join(", ", errors);
    }
}
