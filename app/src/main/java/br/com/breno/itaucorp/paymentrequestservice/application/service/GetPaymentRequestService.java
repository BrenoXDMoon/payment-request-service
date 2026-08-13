package br.com.breno.itaucorp.paymentrequestservice.application.service;

import br.com.breno.itaucorp.paymentrequestservice.application.port.in.GetPaymentRequestUseCase;
import br.com.breno.itaucorp.paymentrequestservice.application.port.out.PaymentRequestRepository;
import br.com.breno.itaucorp.paymentrequestservice.domain.model.PaymentRequest;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetPaymentRequestService implements GetPaymentRequestUseCase {

    private final PaymentRequestRepository repository;

    @Override
    public Optional<PaymentRequest> findById(UUID id) {
        return repository.findById(id);
    }
}
