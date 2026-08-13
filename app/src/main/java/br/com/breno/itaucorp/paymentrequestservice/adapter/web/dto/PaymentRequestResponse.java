package br.com.breno.itaucorp.paymentrequestservice.adapter.web.dto;

import br.com.breno.itaucorp.paymentrequestservice.domain.model.PaymentRequest;
import br.com.breno.itaucorp.paymentrequestservice.domain.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record PaymentRequestResponse(
        UUID id,
        BigDecimal amount,
        String currency,
        String origin,
        String destination,
        String context,
        PaymentStatus status,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        List<EventHistoryResponse> history
) {

    public static PaymentRequestResponse from(PaymentRequest paymentRequest) {
        return new PaymentRequestResponse(
                paymentRequest.getId(),
                paymentRequest.getAmount().amount(),
                paymentRequest.getAmount().currency(),
                paymentRequest.getOrigin(),
                paymentRequest.getDestination(),
                paymentRequest.getContext(),
                paymentRequest.getStatus(),
                paymentRequest.getCreatedAt(),
                paymentRequest.getUpdatedAt(),
                paymentRequest.getHistory().stream().map(EventHistoryResponse::from).toList());
    }
}
