package com.sdd.platform.domain.service.markdown.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarkdownParserCoreTest {

    private final MarkdownParserCore parser = new MarkdownParserCore();

    @Test
    void sections_should_extract_correct_number_of_sections() {
        String content = """
                # Spec Pack

                ## Overview

                Example payload shown to contributors:

                ```
                    const specPack = {
                        overview: {
                            title: 'Spec Pack',
                            description: 'Example payload shown to contributors'
                        },
                        scope: {
                            title: 'Scope',
                            body: 'Real scope body.'
                        }
                    };
                ```

                ## Scope

                Real scope body.
                """;

        List<MarkdownParserCore.MarkdownSection> sections = parser.parse(content, "spec-pack.md").sections();

        assertEquals(3, sections.size(), "Expected 3 sections to be extracted from the markdown content");
    }
}
