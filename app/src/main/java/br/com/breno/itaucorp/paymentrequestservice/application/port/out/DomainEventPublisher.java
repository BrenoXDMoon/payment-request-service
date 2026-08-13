package br.com.breno.itaucorp.paymentrequestservice.application.port.out;

import br.com.breno.itaucorp.paymentrequestservice.domain.event.DomainEvent;

public interface DomainEventPublisher {

    void publish(DomainEvent event);
}