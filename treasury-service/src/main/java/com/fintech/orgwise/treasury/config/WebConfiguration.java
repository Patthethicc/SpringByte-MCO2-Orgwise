package com.fintech.orgwise.treasury.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

/**
 * MVC settings. CORS is handled once at the API gateway, so this service does not enable it.
 */
@Configuration
public class WebConfiguration {

    /** Largest page a client may request from the ledger endpoints. */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * @return customizer capping page sizes at {@value #MAX_PAGE_SIZE} and defaulting to 20
     */
    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer pageableLimits() {
        return resolver -> {
            resolver.setMaxPageSize(MAX_PAGE_SIZE);
            resolver.setFallbackPageable(org.springframework.data.domain.PageRequest.of(0, 20));
        };
    }
}
