package com.farao_community.farao.swe_csa.app;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
public class InterruptConfiguration {

    @Bean
    public Consumer<String> interrupt(InterruptionService interruptionService) {
        return interruptionService::interruption;
    }
}
