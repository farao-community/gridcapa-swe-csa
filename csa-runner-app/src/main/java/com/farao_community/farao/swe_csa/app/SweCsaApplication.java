package com.farao_community.farao.swe_csa.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SuppressWarnings("HideUtilityClassConstructor")
@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
public class SweCsaApplication {
    public static void main(String[] args) {
        SpringApplication.run(SweCsaApplication.class, args);
    }
}
