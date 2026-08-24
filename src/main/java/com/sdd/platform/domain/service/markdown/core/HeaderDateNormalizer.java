package com.sdd.platform.domain.service.markdown.core;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Normalizes header-metadata date values (e.g. {@code **Create date**}/{@code **Update date**})
 * parsed by {@link MarkdownParserCore#extractHeaderMetadata}.
 *
 * Accepts {@code YYYY-MM-DD HH:mm:ss} or bare {@code YYYY-MM-DD}; a bare date is normalized to
 * midnight. Anything else (null, blank, whitespace-only, malformed) yields {@code null} rather
 * than throwing, so callers never need to guard the scan run against a bad date field.
 */
public final class HeaderDateNormalizer {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_ONLY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Header dates are parsed as literal values and represented with a neutral canonical
     * offset to avoid locale-specific semantics.
     */
    private static final ZoneOffset CANONICAL_OFFSET = ZoneOffset.UTC;

    private HeaderDateNormalizer() {
    }

    public static OffsetDateTime normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        try {
            LocalDateTime dateTime = LocalDateTime.parse(trimmed, DATE_TIME_FORMAT);
            return dateTime.atOffset(CANONICAL_OFFSET);
        } catch (DateTimeParseException ignored) {
            // fall through to bare-date attempt
        }

        try {
            LocalDate date = LocalDate.parse(trimmed, DATE_ONLY_FORMAT);
            return date.atStartOfDay().atOffset(CANONICAL_OFFSET);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
