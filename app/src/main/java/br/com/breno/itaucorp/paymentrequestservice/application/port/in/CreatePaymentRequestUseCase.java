package br.com.breno.itaucorp.paymentrequestservice.application.port.in;

import br.com.breno.itaucorp.paymentrequestservice.domain.model.PaymentRequest;

public interface CreatePaymentRequestUseCase {

    PaymentRequest create(CreatePaymentRequestCommand command);
}