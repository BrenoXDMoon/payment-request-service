package br.com.breno.itaucorp.paymentrequestservice.adapter.messaging;

import br.com.breno.itaucorp.paymentrequestservice.application.port.out.DomainEventPublisher;
import br.com.breno.itaucorp.paymentrequestservice.config.KafkaTopicsProperties;
import br.com.breno.itaucorp.paymentrequestservice.domain.event.DomainEvent;
import br.com.breno.itaucorp.paymentrequestservice.domain.event.PaymentRequestCreatedEvent;
import br.com.breno.itaucorp.paymentrequestservice.domain.event.PaymentRequestStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaDomainEventPublisher implements DomainEventPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final KafkaTopicsProperties topics;

    @Override
    public void publish(DomainEvent event) {
        String topic = resolveTopic(event);
        String key = event.paymentRequestId().toString();

        log.info("Publicando evento {} no tópico {}: paymentRequestId={}", event.getClass().getSimpleName(), topic, key);
        kafkaTemplate.send(topic, key, event);
    }

    private String resolveTopic(DomainEvent event) {
        if (event instanceof PaymentRequestCreatedEvent) {
            return topics.paymentRequestCreated();
        }
        if (event instanceof PaymentRequestStatusChangedEvent) {
            return topics.paymentRequestStatusChanged();
        }
        throw new IllegalArgumentException("Unknown domain event type: " + event.getClass().getName());
    }
}
