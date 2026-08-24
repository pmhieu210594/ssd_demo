package com.sdd.platform.web.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoXssFieldsValidatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @NoXssFields(exclude = {"password"})
    record SampleRequest(String name, String password, List<String> tags, List<UUID> ids) {
    }

    @Test
    void rejects_string_field_with_script_tag() {
        var violations = validator.validate(
                new SampleRequest("<script>alert(1)</script>", "safe", List.of(), List.of()));
        assertEquals(1, violations.size());
    }

    @Test
    void allows_excluded_field_to_contain_unsafe_pattern() {
        var violations = validator.validate(
                new SampleRequest("ok", "<script>alert(1)</script>", List.of(), List.of()));
        assertTrue(violations.isEmpty());
    }

    @Test
    void rejects_unsafe_element_inside_string_list() {
        var violations = validator.validate(
                new SampleRequest("ok", "safe", List.of("fine", "<img src=x onerror=alert(1)>"), List.of()));
        assertEquals(1, violations.size());
    }

    @Test
    void ignores_non_string_list_elements() {
        var violations = validator.validate(
                new SampleRequest("ok", "safe", List.of(), List.of(UUID.randomUUID())));
        assertTrue(violations.isEmpty());
    }

    @Test
    void allows_completely_safe_input() {
        var violations = validator.validate(
                new SampleRequest("Acme Corp", "safe", List.of("alpha", "beta"), List.of()));
        assertTrue(violations.isEmpty());
    }
}
