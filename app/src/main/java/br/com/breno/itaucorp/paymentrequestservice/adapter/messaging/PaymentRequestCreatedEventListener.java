package br.com.breno.itaucorp.paymentrequestservice.adapter.messaging;

import br.com.breno.itaucorp.paymentrequestservice.application.port.in.ProcessPaymentRequestUseCase;
import br.com.breno.itaucorp.paymentrequestservice.domain.event.PaymentRequestCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
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
    public void onPaymentRequestCreated(PaymentRequestCreatedEvent event) {
        log.info("Evento recebido: PaymentRequestCreated paymentRequestId={}", event.paymentRequestId());
        processPaymentRequestUseCase.process(event.paymentRequestId());
    }
}
