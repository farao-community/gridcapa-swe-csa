package com.farao_community.farao.swe_csa.app;

import org.springframework.amqp.core.AsyncAmqpTemplate;
import org.springframework.amqp.rabbit.AsyncRabbitTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AmqpConfiguration {

    @Value("${csa-runner.async-time-out}")
    private long asyncTimeOut;

    @Bean
    AsyncAmqpTemplate asyncTemplate(RabbitTemplate rabbitTemplate) {
        AsyncRabbitTemplate asyncTemplate = new AsyncRabbitTemplate(rabbitTemplate);
        asyncTemplate.setReceiveTimeout(asyncTimeOut);
        return asyncTemplate;
    }
}
