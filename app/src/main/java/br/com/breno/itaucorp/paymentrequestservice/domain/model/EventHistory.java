package br.com.breno.itaucorp.paymentrequestservice.domain.model;

import java.time.ZonedDateTime;
import java.util.Objects;

public record EventHistory(PaymentStatus previousStatus, PaymentStatus paymentStatus, ZonedDateTime timestamp, String reason) {

    public EventHistory {
        Objects.requireNonNull(paymentStatus, "paymentStatus must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }
}
