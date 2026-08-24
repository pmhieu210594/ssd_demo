package com.sdd.platform.application.usecase.governance;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditMaskingHelperTest {

    @Test
    void isSensitive_detectsSecretKeywords_caseInsensitively() {
        assertThat(AuditMaskingHelper.isSensitive("passwordHash")).isTrue();
        assertThat(AuditMaskingHelper.isSensitive("api_key")).isTrue();
        assertThat(AuditMaskingHelper.isSensitive("SessionToken")).isTrue();
        assertThat(AuditMaskingHelper.isSensitive("credentialValue")).isTrue();
        assertThat(AuditMaskingHelper.isSensitive("displayName")).isFalse();
    }

    @Test
    void mask_removesSensitiveFieldsAndPreservesSafeValues() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("username", "admin.khoa");
        snapshot.put("displayName", "Admin Khoa");
        snapshot.put("passwordHash", "super-secret");
        snapshot.put("apiKey", "api-secret");
        snapshot.put("status", "ACTIVE");

        Map<String, Object> masked = AuditMaskingHelper.mask(snapshot);

        assertThat(masked).containsEntry("username", "admin.khoa");
        assertThat(masked).containsEntry("displayName", "Admin Khoa");
        assertThat(masked).containsEntry("status", "ACTIVE");
        assertThat(masked).doesNotContainKeys("passwordHash", "apiKey");
    }

    @Test
    void mask_returnsNullForNullSnapshot() {
        assertThat(AuditMaskingHelper.mask(null)).isNull();
    }
}
