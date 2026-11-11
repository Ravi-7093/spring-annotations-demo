package com.demo.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** The logic behind @NotAdminName. Return true = valid, false = error. */
public class NotAdminNameValidator implements ConstraintValidator<NotAdminName, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        boolean valid = value == null || !value.trim().equalsIgnoreCase("admin");
        System.out.println("[@NotAdminName] checking name='" + value + "' -> valid=" + valid);
        return valid; // null is left to @NotBlank (single-responsibility per constraint)
    }
}
