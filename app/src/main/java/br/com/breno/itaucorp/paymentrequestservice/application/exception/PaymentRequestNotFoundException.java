package br.com.breno.itaucorp.paymentrequestservice.application.exception;

import java.util.UUID;

public class PaymentRequestNotFoundException extends RuntimeException {

    public PaymentRequestNotFoundException(UUID id) {
        super("Payment request not found: %s".formatted(id));
    }
}
