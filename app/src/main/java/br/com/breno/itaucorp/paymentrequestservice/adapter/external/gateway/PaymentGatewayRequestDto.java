package br.com.breno.itaucorp.paymentrequestservice.adapter.external.gateway;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentGatewayRequestDto(
        UUID paymentRequestId,
        BigDecimal amount,
        String currency,
        String origin,
        String destination,
        String context
) {
}
