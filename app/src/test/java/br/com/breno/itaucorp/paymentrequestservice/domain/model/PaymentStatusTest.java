package br.com.breno.itaucorp.paymentrequestservice.domain.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStatusTest {

    @ParameterizedTest
    @CsvSource({
            "CREATED, PROCESSING",
            "PROCESSING, COMPLETED",
            "PROCESSING, REJECTED",
            "PROCESSING, FAILED",
    })
    void allowsExpectedTransitions(PaymentStatus from, PaymentStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "CREATED, COMPLETED",
            "CREATED, REJECTED",
            "CREATED, FAILED",
            "CREATED, CREATED",
            "PROCESSING, CREATED",
            "PROCESSING, PROCESSING",
            "COMPLETED, PROCESSING",
            "REJECTED, PROCESSING",
            "FAILED, PROCESSING",
    })
    void rejectsUnexpectedTransitions(PaymentStatus from, PaymentStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"COMPLETED", "REJECTED", "FAILED"})
    void terminalStatesHaveNoAllowedNextStates(PaymentStatus status) {
        assertThat(status.allowedNextStates()).isEmpty();
    }
}
