package br.com.breno.itaucorp.paymentrequestservice.application.port.in;

import java.util.UUID;

public interface ProcessPaymentRequestUseCase {

    void process(UUID paymentRequestId);
}
