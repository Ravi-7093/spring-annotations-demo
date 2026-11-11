package com.demo.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/*
 * CUSTOM CONSTRAINT = an annotation + a ConstraintValidator class.
 *
 * @Constraint(validatedBy = ...) : "when you see @NotAdminName, run NotAdminNameValidator".
 * @Target(FIELD)                 : can only be placed on fields.
 * @Retention(RUNTIME)            : must survive to runtime so the validator can read it.
 *
 * message/groups/payload are REQUIRED members for every Bean Validation constraint.
 */
@Documented
@Constraint(validatedBy = NotAdminNameValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotAdminName {
    String message() default "name cannot be 'admin'";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
