package br.com.breno.itaucorp.paymentrequestservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka.topics")
public record KafkaTopicsProperties(
        String paymentRequestCreated,
        String paymentRequestStatusChanged
) {
}