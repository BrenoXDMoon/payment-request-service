package br.com.breno.itaucorp.paymentrequestservice.domain.model;

import br.com.breno.itaucorp.paymentrequestservice.domain.event.DomainEvent;
import br.com.breno.itaucorp.paymentrequestservice.domain.event.PaymentRequestCreatedEvent;
import br.com.breno.itaucorp.paymentrequestservice.domain.event.PaymentRequestStatusChangedEvent;
import br.com.breno.itaucorp.paymentrequestservice.domain.exception.InvalidStateTransitionException;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentRequestTest {

    private final Money amount = new Money(BigDecimal.valueOf(150), "BRL");
    private final String origin = Instancio.create(String.class);
    private final String destination = Instancio.create(String.class);
    private final String context = Instancio.create(String.class);

    @Test
    void createsPaymentRequestInCreatedStatusWithInitialHistoryAndEvent() {
        PaymentRequest paymentRequest = PaymentRequest.create(amount, origin, destination, context);

        assertThat(paymentRequest.getId()).isNotNull();
        assertThat(paymentRequest.getAmount()).isEqualTo(amount);
        assertThat(paymentRequest.getOrigin()).isEqualTo(origin);
        assertThat(paymentRequest.getDestination()).isEqualTo(destination);
        assertThat(paymentRequest.getContext()).isEqualTo(context);
        assertThat(paymentRequest.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(paymentRequest.getCreatedAt()).isEqualTo(paymentRequest.getUpdatedAt());

        assertThat(paymentRequest.getHistory()).hasSize(1);
        EventHistory initialEntry = paymentRequest.getHistory().get(0);
        assertThat(initialEntry.previousStatus()).isNull();
        assertThat(initialEntry.paymentStatus()).isEqualTo(PaymentStatus.CREATED);

        List<DomainEvent> events = paymentRequest.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOfSatisfying(PaymentRequestCreatedEvent.class, event -> {
            assertThat(event.paymentRequestId()).isEqualTo(paymentRequest.getId());
            assertThat(event.amount()).isEqualTo(amount);
            assertThat(event.origin()).isEqualTo(origin);
            assertThat(event.destination()).isEqualTo(destination);
            assertThat(event.context()).isEqualTo(context);
        });
    }

    @Test
    void pullDomainEventsClearsPendingEvents() {
        PaymentRequest paymentRequest = PaymentRequest.create(amount, origin, destination, context);

        paymentRequest.pullDomainEvents();

        assertThat(paymentRequest.pullDomainEvents()).isEmpty();
    }

    @Test
    void movesFromCreatedToProcessing() {
        PaymentRequest paymentRequest = PaymentRequest.create(amount, origin, destination, context);
        paymentRequest.pullDomainEvents();

        paymentRequest.startProcessing();

        assertThat(paymentRequest.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(paymentRequest.getHistory()).hasSize(2);

        List<DomainEvent> events = paymentRequest.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOfSatisfying(PaymentRequestStatusChangedEvent.class, event -> {
            assertThat(event.previousStatus()).isEqualTo(PaymentStatus.CREATED);
            assertThat(event.newStatus()).isEqualTo(PaymentStatus.PROCESSING);
        });
    }

    @Test
    void movesFromProcessingToCompleted() {
        PaymentRequest paymentRequest = PaymentRequest.create(amount, origin, destination, context);
        paymentRequest.startProcessing();
        paymentRequest.pullDomainEvents();

        paymentRequest.complete();

        assertThat(paymentRequest.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(paymentRequest.getHistory()).hasSize(3);
    }

    @Test
    void movesFromProcessingToRejectedWithReason() {
        PaymentRequest paymentRequest = PaymentRequest.create(amount, origin, destination, context);
        paymentRequest.startProcessing();

        paymentRequest.reject("business rule violation");

        assertThat(paymentRequest.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        EventHistory lastEntry = paymentRequest.getHistory().get(paymentRequest.getHistory().size() - 1);
        assertThat(lastEntry.reason()).isEqualTo("business rule violation");
    }

    @Test
    void movesFromProcessingToFailedWithReason() {
        PaymentRequest paymentRequest = PaymentRequest.create(amount, origin, destination, context);
        paymentRequest.startProcessing();

        paymentRequest.fail("gateway unavailable after retries");

        assertThat(paymentRequest.getStatus()).isEqualTo(PaymentStatus.FAILED);
        EventHistory lastEntry = paymentRequest.getHistory().get(paymentRequest.getHistory().size() - 1);
        assertThat(lastEntry.reason()).isEqualTo("gateway unavailable after retries");
    }

    @Test
    void cannotStartProcessingTwice() {
        PaymentRequest paymentRequest = PaymentRequest.create(amount, origin, destination, context);
        paymentRequest.startProcessing();

        assertThatThrownBy(paymentRequest::startProcessing)
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void cannotCompleteBeforeProcessing() {
        PaymentRequest paymentRequest = PaymentRequest.create(amount, origin, destination, context);

        assertThatThrownBy(paymentRequest::complete)
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void terminalStatusesRejectFurtherTransitions() {
        PaymentRequest completed = PaymentRequest.create(amount, origin, destination, context);
        completed.startProcessing();
        completed.complete();

        assertThatThrownBy(completed::complete).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> completed.reject("late rejection")).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> completed.fail("late failure")).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void historyIsExposedAsAnImmutableSnapshot() {
        PaymentRequest paymentRequest = PaymentRequest.create(amount, origin, destination, context);

        List<EventHistory> history = paymentRequest.getHistory();

        assertThatThrownBy(() -> history.add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsBlankOrigin() {
        assertThatThrownBy(() -> PaymentRequest.create(amount, " ", destination, context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("origin");
    }
}
