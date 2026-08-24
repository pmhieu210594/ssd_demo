package com.sdd.platform.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level constraint applied to request DTOs (records) to reject any {@code String} or
 * {@code Collection<String>} record component that contains a common XSS payload shape.
 * Shared across every insert/update endpoint so the check cannot be bypassed by a client
 * (e.g. Postman) that skips the frontend.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NoXssFieldsValidator.class)
public @interface NoXssFields {

    String message() default "UNSAFE_INPUT";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Names of record components to skip (e.g. {@code password}, {@code confirmPassword}).
     */
    String[] exclude() default {};
}
