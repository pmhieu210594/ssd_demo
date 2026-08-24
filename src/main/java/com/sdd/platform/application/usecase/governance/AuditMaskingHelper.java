package com.sdd.platform.application.usecase.governance;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Centralized whitelist-style masking for admin audit-log snapshots.
 * A field is dropped entirely (never stored, not even as null) if its name
 * matches a sensitive pattern; {@code changed_fields} is computed separately
 * from the unmasked snapshot so the field name is still visible without its value.
 */
public final class AuditMaskingHelper {

    private static final String[] SENSITIVE_KEYWORDS = {
            "password", "token", "secret", "apikey", "api_key", "credential"
    };

    private AuditMaskingHelper() {
    }

    public static boolean isSensitive(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String normalized = fieldName.toLowerCase(Locale.ROOT);
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public static Map<String, Object> mask(Map<String, Object> snapshot) {
        if (snapshot == null) {
            return null;
        }
        Map<String, Object> masked = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
            if (!isSensitive(entry.getKey())) {
                masked.put(entry.getKey(), entry.getValue());
            }
        }
        return masked;
    }
}
