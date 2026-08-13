package br.com.breno.itaucorp.paymentrequestservice.application.port.out;

public interface PaymentGatewayPort {

    PaymentGatewayResponse process(PaymentGatewayRequest request);
}
