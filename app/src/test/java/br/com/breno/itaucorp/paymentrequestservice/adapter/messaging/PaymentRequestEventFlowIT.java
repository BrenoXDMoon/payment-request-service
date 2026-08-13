package br.com.breno.itaucorp.paymentrequestservice.adapter.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.breno.itaucorp.paymentrequestservice.application.port.in.CreatePaymentRequestCommand;
import br.com.breno.itaucorp.paymentrequestservice.application.port.in.CreatePaymentRequestUseCase;
import br.com.breno.itaucorp.paymentrequestservice.application.port.out.PaymentRequestRepository;
import br.com.breno.itaucorp.paymentrequestservice.domain.model.EventHistory;
import br.com.breno.itaucorp.paymentrequestservice.domain.model.PaymentRequest;
import br.com.breno.itaucorp.paymentrequestservice.domain.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@Testcontainers
class PaymentRequestEventFlowIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private CreatePaymentRequestUseCase createPaymentRequestUseCase;

    @Autowired
    private PaymentRequestRepository repository;

    @Test
    void createdPaymentRequestIsAutomaticallyProcessedThroughKafkaAndRealGateway() {
        CreatePaymentRequestCommand command = new CreatePaymentRequestCommand(
                BigDecimal.valueOf(250), "BRL", "acc-origin", "acc-destination", "order-e2e-1");

        PaymentRequest created = createPaymentRequestUseCase.create(command);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            PaymentRequest reloaded = repository.findById(created.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(reloaded.getHistory())
                    .extracting(EventHistory::paymentStatus)
                    .containsExactly(PaymentStatus.CREATED, PaymentStatus.PROCESSING, PaymentStatus.COMPLETED);
        });
    }

    @Test
    void createdPaymentRequestIsRejectedByRealGatewayWhenAmountExceedsApprovalLimit() {
        CreatePaymentRequestCommand command = new CreatePaymentRequestCommand(
                BigDecimal.valueOf(15_000), "BRL", "acc-origin", "acc-destination", "order-e2e-2");

        PaymentRequest created = createPaymentRequestUseCase.create(command);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            PaymentRequest reloaded = repository.findById(created.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.REJECTED);
            assertThat(reloaded.getHistory())
                    .extracting(EventHistory::paymentStatus)
                    .containsExactly(PaymentStatus.CREATED, PaymentStatus.PROCESSING, PaymentStatus.REJECTED);
        });
    }
}
