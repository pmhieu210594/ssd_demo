package com.sdd.platform.domain.service.markdown.core;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HeaderDateNormalizerTest {

    private static final ZoneOffset EXPECTED_OFFSET = ZoneOffset.UTC;

    @Test
    void normalize_fullDateTime_passesThrough() {
        OffsetDateTime result = HeaderDateNormalizer.normalize("2026-08-21 09:30:00");
        assertEquals(OffsetDateTime.of(2026, 8, 21, 9, 30, 0, 0, EXPECTED_OFFSET), result);
    }

    @Test
    void normalize_bareDate_normalizesToMidnight() {
        OffsetDateTime result = HeaderDateNormalizer.normalize("2026-08-21");
        assertEquals(OffsetDateTime.of(2026, 8, 21, 0, 0, 0, 0, EXPECTED_OFFSET), result);
    }

    @Test
    void normalize_null_returnsNull() {
        assertNull(HeaderDateNormalizer.normalize(null));
    }

    @Test
    void normalize_empty_returnsNull() {
        assertNull(HeaderDateNormalizer.normalize(""));
    }

    @Test
    void normalize_whitespaceOnly_returnsNull() {
        assertNull(HeaderDateNormalizer.normalize("   "));
    }

    @Test
    void normalize_nonDateText_returnsNull() {
        assertNull(HeaderDateNormalizer.normalize("TBD"));
    }

    @Test
    void normalize_fullWidthDigits_returnsNull() {
        assertNull(HeaderDateNormalizer.normalize("２０２６-０８-２１"));
    }
}
