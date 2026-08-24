package com.sdd.platform.web.validation;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.util.List;
import java.util.Locale;

/**
 * Detector for XSS payload shapes. Used by {@link NoXssFieldsValidator} to reject request
 * bodies before they reach the domain/persistence layers.
 *
 * <p>HTML tag/attribute structures (script, iframe, svg, on-event handlers on any element,
 * unknown/custom tags, malformed markup, ...) are detected by parsing the value as HTML with
 * jsoup and rejecting anything a {@link Safelist#none()} sanitizer would strip. jsoup parses
 * real HTML rather than matching a fixed list of tag names, so it also catches tags never
 * explicitly enumerated.
 *
 * <p>Dangerous URI schemes as bare text (not wrapped in any tag/attribute, so invisible to a
 * tag-based parser) are still checked separately below.
 */
public final class XssGuard {

    private static final List<String> DANGEROUS_URI_SCHEMES = List.of(
            "javascript:",
            "vbscript:",
            "data:text/html"
    );

    private XssGuard() {
    }

    public static boolean containsUnsafeContent(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return !Jsoup.isValid(value, Safelist.none()) || containsDangerousUriScheme(value);
    }

    private static boolean containsDangerousUriScheme(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return DANGEROUS_URI_SCHEMES.stream().anyMatch(lower::contains);
    }
}
