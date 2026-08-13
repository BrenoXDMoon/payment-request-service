package br.com.breno.itaucorp.paymentrequestservice.adapter.persistence;

import br.com.breno.itaucorp.paymentrequestservice.adapter.persistence.entity.PaymentRequestJpaEntity;
import br.com.breno.itaucorp.paymentrequestservice.adapter.persistence.mapper.PaymentRequestEntityMapper;
import br.com.breno.itaucorp.paymentrequestservice.application.port.out.PaymentRequestRepository;
import br.com.breno.itaucorp.paymentrequestservice.domain.model.PaymentRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PaymentRequestRepositoryAdapter implements PaymentRequestRepository {

    private final PaymentRequestJpaRepository jpaRepository;

    public PaymentRequestRepositoryAdapter(PaymentRequestJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public PaymentRequest save(PaymentRequest paymentRequest) {
        PaymentRequestJpaEntity entity = jpaRepository.findById(paymentRequest.getId())
                .map(existing -> PaymentRequestEntityMapper.updateEntity(existing, paymentRequest))
                .orElseGet(() -> PaymentRequestEntityMapper.toEntity(paymentRequest));

        PaymentRequestJpaEntity saved = jpaRepository.save(entity);
        return PaymentRequestEntityMapper.toDomain(saved);
    }

    @Override
    public Optional<PaymentRequest> findById(UUID id) {
        return jpaRepository.findById(id).map(PaymentRequestEntityMapper::toDomain);
    }
}
