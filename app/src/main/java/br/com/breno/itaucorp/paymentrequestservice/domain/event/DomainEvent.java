package br.com.breno.itaucorp.paymentrequestservice.domain.event;

import java.time.ZonedDateTime;
import java.util.UUID;

public interface DomainEvent {

    UUID paymentRequestId();

    ZonedDateTime occurredAt();
}
