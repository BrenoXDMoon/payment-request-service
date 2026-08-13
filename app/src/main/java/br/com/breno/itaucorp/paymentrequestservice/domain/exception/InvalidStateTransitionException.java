package br.com.breno.itaucorp.paymentrequestservice.domain.exception;

import br.com.breno.itaucorp.paymentrequestservice.domain.model.PaymentStatus;

public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(PaymentStatus from, PaymentStatus to) {
        super("Invalid payment request status transition: %s -> %s".formatted(from, to));
    }
}
