package br.com.breno.itaucorp.paymentrequestservice.adapter.web.dto;

import br.com.breno.itaucorp.paymentrequestservice.domain.model.EventHistory;
import br.com.breno.itaucorp.paymentrequestservice.domain.model.PaymentStatus;
import java.time.ZonedDateTime;

public record EventHistoryResponse(
        PaymentStatus previousStatus,
        PaymentStatus newStatus,
        ZonedDateTime timestamp,
        String reason
) {

    public static EventHistoryResponse from(EventHistory eventHistory) {
        return new EventHistoryResponse(
                eventHistory.previousStatus(),
                eventHistory.paymentStatus(),
                eventHistory.timestamp(),
                eventHistory.reason());
    }
}
