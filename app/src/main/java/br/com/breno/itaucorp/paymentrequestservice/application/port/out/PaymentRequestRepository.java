package br.com.breno.itaucorp.paymentrequestservice.application.port.out;

import br.com.breno.itaucorp.paymentrequestservice.domain.model.PaymentRequest;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRequestRepository {

    PaymentRequest save(PaymentRequest paymentRequest);

    Optional<PaymentRequest> findById(UUID id);
}
