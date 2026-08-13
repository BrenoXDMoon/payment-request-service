package br.com.breno.itaucorp.paymentrequestservice.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.breno.itaucorp.paymentrequestservice.domain.model.EventHistory;
import br.com.breno.itaucorp.paymentrequestservice.domain.model.Money;
import br.com.breno.itaucorp.paymentrequestservice.domain.model.PaymentRequest;
import br.com.breno.itaucorp.paymentrequestservice.domain.model.PaymentStatus;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PaymentRequestRepositoryAdapter.class)
@Testcontainers
class PaymentRequestRepositoryAdapterIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private PaymentRequestRepositoryAdapter repository;

    @Test
    void savesAndReloadsPaymentRequestWithHistory() {
        PaymentRequest created = PaymentRequest.create(new Money(BigDecimal.TEN, "BRL"), "acc-1", "acc-2", "order-123");

        repository.save(created);

        Optional<PaymentRequest> found = repository.findById(created.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(found.get().getAmount().amount()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(found.get().getAmount().currency()).isEqualTo("BRL");
        assertThat(found.get().getHistory()).hasSize(1);
        assertThat(found.get().getHistory().get(0).paymentStatus()).isEqualTo(PaymentStatus.CREATED);
    }

    @Test
    void persistsOnlyNewHistoryEntriesOnResave() {
        PaymentRequest paymentRequest = PaymentRequest.create(new Money(BigDecimal.TEN, "BRL"), "acc-1", "acc-2", "order-123");
        repository.save(paymentRequest);

        paymentRequest.startProcessing();
        repository.save(paymentRequest);

        Optional<PaymentRequest> reloaded = repository.findById(paymentRequest.getId());

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(reloaded.get().getHistory())
                .extracting(EventHistory::paymentStatus)
                .containsExactly(PaymentStatus.CREATED, PaymentStatus.PROCESSING);
    }

    @Test
    void returnsEmptyWhenPaymentRequestNotFound() {
        Optional<PaymentRequest> found = repository.findById(UUID.randomUUID());

        assertThat(found).isEmpty();
    }
}
