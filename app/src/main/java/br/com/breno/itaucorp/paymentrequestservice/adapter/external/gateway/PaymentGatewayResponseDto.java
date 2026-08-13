package br.com.breno.itaucorp.paymentrequestservice.adapter.external.gateway;

public record PaymentGatewayResponseDto(
        PaymentGatewayOutcomeDto outcome,
        String reason
) {
}
