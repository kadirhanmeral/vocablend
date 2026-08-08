package com.vocablend.vocablend_be.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// Injected wherever scheduling happens so tests can pin time with Clock.fixed
// instead of asserting against wall-clock arithmetic.
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
