package com.sdd.platform.config;

import com.sdd.platform.domain.service.ArtifactNormalizer;
import com.sdd.platform.domain.service.markdown.reviewchecklist.ReviewChecklistMarkdownParser;
import com.sdd.platform.domain.service.markdown.selfreview.SelfReviewMarkdownParser;
import com.sdd.platform.domain.service.markdown.specpack.SpecPackMarkdownParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires Spring-free domain services as Spring beans so the rest of the
 * application can inject them through the container.
 *
 * Domain classes themselves must not carry Spring annotations
 * (see {@code com.sdd.platform.domain} package-info).
 */
@Configuration
public class DomainConfig {

    @Bean
    public ArtifactNormalizer artifactNormalizer() {
        return new ArtifactNormalizer();
    }

    @Bean
    public SpecPackMarkdownParser specPackMarkdownParser() {
        return new SpecPackMarkdownParser();
    }

    @Bean
    public SelfReviewMarkdownParser selfReviewMarkdownParser() {
        return new SelfReviewMarkdownParser();
    }

    @Bean
    public ReviewChecklistMarkdownParser reviewChecklistMarkdownParser() {
        return new ReviewChecklistMarkdownParser();
    }
}
