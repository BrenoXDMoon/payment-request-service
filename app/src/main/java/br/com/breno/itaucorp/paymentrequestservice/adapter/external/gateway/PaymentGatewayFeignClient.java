package br.com.breno.itaucorp.paymentrequestservice.adapter.external.gateway;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-gateway", url = "${app.payment-gateway.base-url}")
public interface PaymentGatewayFeignClient {

    @PostMapping("/process")
    PaymentGatewayResponseDto process(@RequestBody PaymentGatewayRequestDto request);
}
