package br.com.breno.itaucorp.paymentrequestservice.adapter.messaging;

import br.com.breno.itaucorp.paymentrequestservice.application.port.in.ProcessPaymentRequestUseCase;
import br.com.breno.itaucorp.paymentrequestservice.domain.event.PaymentRequestCreatedEvent;
import br.com.breno.itaucorp.paymentrequestservice.observability.CorrelationIdContext;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRequestCreatedEventListener {

    private final ProcessPaymentRequestUseCase processPaymentRequestUseCase;

    @KafkaListener(
            topics = "${app.kafka.topics.payment-request-created}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onPaymentRequestCreated(
            PaymentRequestCreatedEvent event,
            @Header(value = CorrelationIdContext.KAFKA_HEADER, required = false) byte[] correlationIdHeader) {
        var correlationId = getCorrelationId(correlationIdHeader);

        MDC.put(CorrelationIdContext.MDC_KEY, correlationId);
        try {
            log.info("Evento recebido: PaymentRequestCreated paymentRequestId={}", event.paymentRequestId());
            processPaymentRequestUseCase.process(event.paymentRequestId());
        } finally {
            MDC.remove(CorrelationIdContext.MDC_KEY);
        }
    }

    private String getCorrelationId(byte[] correlationIdHeader) {
        return correlationIdHeader != null
                ? new String(correlationIdHeader, StandardCharsets.UTF_8)
                : UUID.randomUUID().toString();
    }
}
