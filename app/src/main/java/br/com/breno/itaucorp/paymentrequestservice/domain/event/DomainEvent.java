package br.com.breno.itaucorp.paymentrequestservice.domain.event;

import java.time.ZonedDateTime;

public interface DomainEvent {

    ZonedDateTime occurredAt();
}
