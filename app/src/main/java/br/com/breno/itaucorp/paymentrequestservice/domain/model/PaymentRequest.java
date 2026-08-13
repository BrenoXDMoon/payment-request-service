package br.com.breno.itaucorp.paymentrequestservice.domain.model;

import br.com.breno.itaucorp.paymentrequestservice.domain.event.DomainEvent;
import br.com.breno.itaucorp.paymentrequestservice.domain.event.PaymentRequestCreatedEvent;
import br.com.breno.itaucorp.paymentrequestservice.domain.event.PaymentRequestStatusChangedEvent;
import br.com.breno.itaucorp.paymentrequestservice.domain.exception.InvalidStateTransitionException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode
public class PaymentRequest {

    private final UUID id;
    private final Money amount;
    private final String origin;
    private final String destination;
    private final String context;
    private final ZonedDateTime createdAt;
    private final List<EventHistory> history = new ArrayList<>();
    private final List<DomainEvent> domainEvents = new ArrayList<>();
    private PaymentStatus status;
    private ZonedDateTime updatedAt;

    private PaymentRequest(UUID id, Money amount, String origin, String destination, String context,
                            PaymentStatus status, ZonedDateTime createdAt, ZonedDateTime updatedAt) {
        this.id = id;
        this.amount = amount;
        this.origin = origin;
        this.destination = destination;
        this.context = context;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static PaymentRequest create(Money amount, String origin, String destination, String context) {
        Objects.requireNonNull(amount, "amount must not be null");
        requireNonBlank(origin, "origin");
        requireNonBlank(destination, "destination");
        requireNonBlank(context, "context");

        ZonedDateTime now = ZonedDateTime.now();
        PaymentRequest paymentRequest = new PaymentRequest(
                UUID.randomUUID(), amount, origin, destination, context, PaymentStatus.CREATED, now, now);

        paymentRequest.history.add(new EventHistory(null, PaymentStatus.CREATED, now, "Payment request created"));
        paymentRequest.domainEvents.add(
                new PaymentRequestCreatedEvent(paymentRequest.id, amount, origin, destination, context, now));

        return paymentRequest;
    }

    public static PaymentRequest reconstitute(UUID id, Money amount, String origin, String destination, String context,
                                               PaymentStatus status, ZonedDateTime createdAt, ZonedDateTime updatedAt,
                                               List<EventHistory> history) {
        PaymentRequest paymentRequest = new PaymentRequest(id, amount, origin, destination, context, status, createdAt, updatedAt);
        paymentRequest.history.addAll(history);
        return paymentRequest;
    }

    public void startProcessing() {
        transitionTo(PaymentStatus.PROCESSING, "Processing started");
    }

    public void complete() {
        transitionTo(PaymentStatus.COMPLETED, "Payment completed successfully");
    }

    public void reject(String reason) {
        transitionTo(PaymentStatus.REJECTED, reason);
    }

    public void fail(String reason) {
        transitionTo(PaymentStatus.FAILED, reason);
    }

    private void transitionTo(PaymentStatus paymentStatus, String reason) {
        if (!status.canTransitionTo(paymentStatus)) {
            throw new InvalidStateTransitionException(status, paymentStatus);
        }

        PaymentStatus previousStatus = status;
        ZonedDateTime now = ZonedDateTime.now();

        status = paymentStatus;
        updatedAt = now;
        history.add(new EventHistory(previousStatus, paymentStatus, now, reason));
        domainEvents.add(new PaymentRequestStatusChangedEvent(id, previousStatus, paymentStatus, reason, now));
    }

    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (Objects.isNull(value) || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    public List<EventHistory> getHistory() {
        return List.copyOf(history);
    }
}
