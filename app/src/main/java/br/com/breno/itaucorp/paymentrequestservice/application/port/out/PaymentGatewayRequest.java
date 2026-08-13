package br.com.breno.itaucorp.paymentrequestservice.application.port.out;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentGatewayRequest(
        UUID paymentRequestId,
        BigDecimal amount,
        String currency,
        String origin,
        String destination,
        String context
) {
}
