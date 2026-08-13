package br.com.breno.itaucorp.paymentrequestservice.domain.exception;

public class PaymentRejectedException extends RuntimeException {

    public PaymentRejectedException(String reason) {
        super(reason);
    }
}
