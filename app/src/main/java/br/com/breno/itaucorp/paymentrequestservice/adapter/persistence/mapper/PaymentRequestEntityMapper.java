package br.com.breno.itaucorp.paymentrequestservice.adapter.persistence.mapper;

import br.com.breno.itaucorp.paymentrequestservice.adapter.persistence.entity.EventHistoryJpaEntity;
import br.com.breno.itaucorp.paymentrequestservice.adapter.persistence.entity.PaymentRequestJpaEntity;
import br.com.breno.itaucorp.paymentrequestservice.domain.model.EventHistory;
import br.com.breno.itaucorp.paymentrequestservice.domain.model.Money;
import br.com.breno.itaucorp.paymentrequestservice.domain.model.PaymentRequest;
import br.com.breno.itaucorp.paymentrequestservice.domain.model.PaymentStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class PaymentRequestEntityMapper {

    private PaymentRequestEntityMapper() {
    }

    public static PaymentRequestJpaEntity toEntity(PaymentRequest paymentRequest) {
        var entity = new PaymentRequestJpaEntity();

        entity.setId(paymentRequest.getId());
        entity.setAmount(paymentRequest.getAmount().amount());
        entity.setCurrency(paymentRequest.getAmount().currency());
        entity.setOrigin(paymentRequest.getOrigin());
        entity.setDestination(paymentRequest.getDestination());
        entity.setContext(paymentRequest.getContext());
        entity.setStatus(paymentRequest.getStatus());
        entity.setCreatedAt(paymentRequest.getCreatedAt());
        entity.setUpdatedAt(paymentRequest.getUpdatedAt());

        paymentRequest.getHistory().forEach(eventHistory -> entity.getHistory().add(toEntity(eventHistory, entity)));

        return entity;
    }

    public static PaymentRequestJpaEntity updateEntity(PaymentRequestJpaEntity entity, PaymentRequest paymentRequest) {
        entity.setStatus(paymentRequest.getStatus());
        entity.setUpdatedAt(paymentRequest.getUpdatedAt());

        var persistedStatuses = entity.getHistory().stream()
                .map(EventHistoryJpaEntity::getNewStatus)
                .collect(Collectors.toSet());

        paymentRequest.getHistory().stream()
                .filter(eventHistory -> !persistedStatuses.contains(eventHistory.paymentStatus()))
                .forEach(eventHistory -> entity.getHistory().add(toEntity(eventHistory, entity)));

        return entity;
    }

    public static PaymentRequest toDomain(PaymentRequestJpaEntity entity) {
        var history = entity.getHistory().stream()
                .sorted(Comparator.comparing(EventHistoryJpaEntity::getTimestamp))
                .map(PaymentRequestEntityMapper::toDomain)
                .toList();

        var money = new Money(entity.getAmount(), entity.getCurrency());

        return PaymentRequest.reconstitute(
                entity.getId(),
                money,
                entity.getOrigin(),
                entity.getDestination(),
                entity.getContext(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                history
        );
    }

    private static EventHistoryJpaEntity toEntity(EventHistory eventHistory, PaymentRequestJpaEntity paymentRequest) {
        var entity = new EventHistoryJpaEntity();
        entity.setPaymentRequest(paymentRequest);
        entity.setPreviousStatus(eventHistory.previousStatus());
        entity.setNewStatus(eventHistory.paymentStatus());
        entity.setTimestamp(eventHistory.timestamp());
        entity.setReason(eventHistory.reason());

        return entity;
    }

    private static EventHistory toDomain(EventHistoryJpaEntity entity) {
        return new EventHistory(entity.getPreviousStatus(), entity.getNewStatus(), entity.getTimestamp(), entity.getReason());
    }
}
