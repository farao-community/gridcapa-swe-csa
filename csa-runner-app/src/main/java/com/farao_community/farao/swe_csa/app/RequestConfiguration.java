package com.farao_community.farao.swe_csa.app;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

import java.util.function.Function;

@Configuration
public class RequestConfiguration {

    @Bean
    public Function<Flux<byte[]>, Flux<byte[]>> request(CsaRunner csaRunner) {
        return csaRequestFlux -> csaRequestFlux
                .map(csaRunner::launchCsaRequest)
                .log();
    }

}