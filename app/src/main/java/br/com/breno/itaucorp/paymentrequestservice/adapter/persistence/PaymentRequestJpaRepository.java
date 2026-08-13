package br.com.breno.itaucorp.paymentrequestservice.adapter.persistence;

import br.com.breno.itaucorp.paymentrequestservice.adapter.persistence.entity.PaymentRequestJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRequestJpaRepository extends JpaRepository<PaymentRequestJpaEntity, UUID> {
}
