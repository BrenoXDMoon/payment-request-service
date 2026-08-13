package br.com.breno.itaucorp.paymentrequestservice.adapter.external.gateway;

import br.com.breno.itaucorp.paymentrequestservice.application.port.out.PaymentGatewayOutcome;
import br.com.breno.itaucorp.paymentrequestservice.application.port.out.PaymentGatewayPort;
import br.com.breno.itaucorp.paymentrequestservice.application.port.out.PaymentGatewayRequest;
import br.com.breno.itaucorp.paymentrequestservice.application.port.out.PaymentGatewayResponse;
import br.com.breno.itaucorp.paymentrequestservice.domain.exception.PaymentGatewayUnavailableException;
import feign.FeignException;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentGatewayAdapter implements PaymentGatewayPort {

    private final PaymentGatewayFeignClient feignClient;

    @Override
    @Retry(name = "paymentGateway")
    public PaymentGatewayResponse process(PaymentGatewayRequest request) {
        PaymentGatewayRequestDto requestDto = new PaymentGatewayRequestDto(
                request.paymentRequestId(),
                request.amount(),
                request.currency(),
                request.origin(),
                request.destination(),
                request.context());

        try {
            PaymentGatewayResponseDto responseDto = feignClient.process(requestDto);
            return new PaymentGatewayResponse(
                    PaymentGatewayOutcome.valueOf(responseDto.outcome().name()),
                    responseDto.reason());
        } catch (FeignException ex) {
            log.warn("Gateway de pagamento retornou erro: paymentRequestId={}, status={}",
                    request.paymentRequestId(), ex.status());
            throw new PaymentGatewayUnavailableException("Payment gateway unavailable: HTTP " + ex.status(), ex);
        }
    }
}
