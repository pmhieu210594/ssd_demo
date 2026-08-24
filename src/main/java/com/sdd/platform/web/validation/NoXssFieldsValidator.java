package com.sdd.platform.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.Set;

/**
 * Reflects over the record components of the annotated DTO and rejects any {@code String} or
 * {@code Collection<String>} value flagged by {@link XssGuard}. Non-string record components
 * (UUID, enums, numbers, {@code List<UUID>}, ...) are left untouched.
 */
public class NoXssFieldsValidator implements ConstraintValidator<NoXssFields, Object> {

    private Set<String> excluded;

    @Override
    public void initialize(NoXssFields annotation) {
        excluded = Set.of(annotation.exclude());
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        boolean valid = true;
        context.disableDefaultConstraintViolation();

        for (RecordComponent component : value.getClass().getRecordComponents()) {
            if (excluded.contains(component.getName())) {
                continue;
            }

            Object componentValue;
            try {
                componentValue = component.getAccessor().invoke(value);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unable to read record component " + component.getName(), e);
            }

            if (isUnsafe(componentValue)) {
                context.buildConstraintViolationWithTemplate("UNSAFE_INPUT")
                        .addPropertyNode(component.getName())
                        .addConstraintViolation();
                valid = false;
            }
        }

        return valid;
    }

    private boolean isUnsafe(Object componentValue) {
        if (componentValue instanceof String stringValue) {
            return XssGuard.containsUnsafeContent(stringValue);
        }
        if (componentValue instanceof Collection<?> collectionValue) {
            return collectionValue.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .anyMatch(XssGuard::containsUnsafeContent);
        }
        return false;
    }
}
