package br.com.breno.itaucorp.paymentrequestservice.adapter.messaging;

import br.com.breno.itaucorp.paymentrequestservice.application.port.out.DomainEventPublisher;
import br.com.breno.itaucorp.paymentrequestservice.config.KafkaTopicsProperties;
import br.com.breno.itaucorp.paymentrequestservice.domain.event.DomainEvent;
import br.com.breno.itaucorp.paymentrequestservice.domain.event.PaymentRequestCreatedEvent;
import br.com.breno.itaucorp.paymentrequestservice.domain.event.PaymentRequestStatusChangedEvent;
import br.com.breno.itaucorp.paymentrequestservice.observability.CorrelationIdContext;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaDomainEventPublisher implements DomainEventPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final KafkaTopicsProperties topics;

    @Override
    public void publish(DomainEvent event) {
        var topic = resolveTopic(event);
        var key = event.paymentRequestId().toString();

        log.info("Publicando evento {} no tópico {}: paymentRequestId={}", event.getClass().getSimpleName(), topic, key);

        var record = new ProducerRecord<Object, Object>(topic, key, event);
        var correlationId = MDC.get(CorrelationIdContext.MDC_KEY);
        if (StringUtils.hasText(correlationId)) {
            record.headers().add(CorrelationIdContext.KAFKA_HEADER, correlationId.getBytes(StandardCharsets.UTF_8));
        }
        kafkaTemplate.send(record);
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
