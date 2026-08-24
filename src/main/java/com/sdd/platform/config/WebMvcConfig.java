package com.sdd.platform.config;

import com.sdd.platform.web.security.CurrentAppUserResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Registers controller argument resolvers so {@code @CurrentUser AppUser caller}
 * works in any controller.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentAppUserResolver currentAppUserResolver;

    public WebMvcConfig(CurrentAppUserResolver currentAppUserResolver) {
        this.currentAppUserResolver = currentAppUserResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentAppUserResolver);
    }
}
