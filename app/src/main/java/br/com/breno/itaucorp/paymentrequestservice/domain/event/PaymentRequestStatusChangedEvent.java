package br.com.breno.itaucorp.paymentrequestservice.domain.event;

import br.com.breno.itaucorp.paymentrequestservice.domain.model.PaymentStatus;
import java.time.ZonedDateTime;
import java.util.UUID;

public record PaymentRequestStatusChangedEvent(
        UUID paymentRequestId,
        PaymentStatus previousStatus,
        PaymentStatus newStatus,
        String reason,
        ZonedDateTime occurredAt
) implements DomainEvent {
}
