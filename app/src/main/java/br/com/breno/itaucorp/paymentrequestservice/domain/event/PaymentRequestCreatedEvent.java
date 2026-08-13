package br.com.breno.itaucorp.paymentrequestservice.domain.event;

import br.com.breno.itaucorp.paymentrequestservice.domain.model.Money;
import java.time.ZonedDateTime;
import java.util.UUID;

public record PaymentRequestCreatedEvent(
        UUID paymentRequestId,
        Money amount,
        String origin,
        String destination,
        String context,
        ZonedDateTime occurredAt
) implements DomainEvent {
}
