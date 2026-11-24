package com.fintech.orgwise.treasury.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Turns on OpenFeign clients and scheduling (the reconciler). Kept apart from the application
 * class so MVC/JPA test slices do not try to build Feign clients.
 */
@Configuration
@EnableFeignClients(basePackages = "com.fintech.orgwise.treasury.client")
@EnableScheduling
public class ClientConfiguration {

    /**
     * @return the system UTC clock, injectable so time-based logic can be tested
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
