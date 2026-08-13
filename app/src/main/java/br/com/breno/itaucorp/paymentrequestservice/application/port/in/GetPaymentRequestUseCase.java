package br.com.breno.itaucorp.paymentrequestservice.application.port.in;

import br.com.breno.itaucorp.paymentrequestservice.domain.model.PaymentRequest;
import java.util.Optional;
import java.util.UUID;

public interface GetPaymentRequestUseCase {

    Optional<PaymentRequest> findById(UUID id);
}
