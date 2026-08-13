package br.com.breno.itaucorp.paymentrequestservice.application.port.out;

public record PaymentGatewayResponse(
        PaymentGatewayOutcome outcome,
        String reason
) {
}
