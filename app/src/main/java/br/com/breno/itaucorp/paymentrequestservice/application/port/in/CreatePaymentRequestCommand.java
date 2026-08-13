package br.com.breno.itaucorp.paymentrequestservice.application.port.in;

import java.math.BigDecimal;

public record CreatePaymentRequestCommand(
        BigDecimal amount,
        String currency,
        String origin,
        String destination,
        String context
) {
}