package com.sdd.platform.web.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XssGuardTest {

    @Test
    void detects_script_tag() {
        assertTrue(XssGuard.containsUnsafeContent("<script>alert('x')</script>"));
    }

    @Test
    void detects_event_handler_attribute() {
        assertTrue(XssGuard.containsUnsafeContent("<img src=x onerror=alert(1)>"));
    }

    @Test
    void detects_javascript_uri() {
        assertTrue(XssGuard.containsUnsafeContent("javascript:alert(1)"));
    }

    @Test
    void detects_iframe_tag() {
        assertTrue(XssGuard.containsUnsafeContent("<iframe src=\"evil\"></iframe>"));
    }

    @Test
    void allows_plain_text() {
        assertFalse(XssGuard.containsUnsafeContent("Acme Corporation"));
    }

    @Test
    void allows_null_and_empty() {
        assertFalse(XssGuard.containsUnsafeContent(null));
        assertFalse(XssGuard.containsUnsafeContent(""));
    }

    @Test
    void detects_tag_not_in_any_manual_blocklist() {
        assertTrue(XssGuard.containsUnsafeContent("<marquee>scroll</marquee>"));
        assertTrue(XssGuard.containsUnsafeContent("<link rel=\"stylesheet\" href=\"evil.css\">"));
    }

    @Test
    void detects_mixed_case_tag() {
        assertTrue(XssGuard.containsUnsafeContent("<ScRiPt>alert(1)</ScRiPt>"));
    }

    @Test
    void detects_event_handler_on_arbitrary_tag() {
        assertTrue(XssGuard.containsUnsafeContent("<div onclick=\"doEvil()\">click me</div>"));
    }

    @Test
    void treats_stray_angle_brackets_as_unsafe_markup() {
        // jsoup parses any "<...>" span conservatively (bogus-comment-like), even when it
        // isn't a real tag — stricter than the old regex, so plain text using "<"/">" as
        // comparison operators should avoid this shape in fields covered by @NoXssFields.
        assertTrue(XssGuard.containsUnsafeContent("Value < 10 and > 5"));
    }
}
