package br.com.breno.itaucorp.paymentrequestservice.adapter.external.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import br.com.breno.itaucorp.paymentrequestservice.application.port.out.PaymentGatewayOutcome;
import br.com.breno.itaucorp.paymentrequestservice.application.port.out.PaymentGatewayRequest;
import br.com.breno.itaucorp.paymentrequestservice.application.port.out.PaymentGatewayResponse;
import br.com.breno.itaucorp.paymentrequestservice.domain.exception.PaymentGatewayUnavailableException;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayAdapterTest {

    @Mock
    private PaymentGatewayFeignClient feignClient;

    private PaymentGatewayAdapter adapter;

    private PaymentGatewayRequest newRequest() {
        return new PaymentGatewayRequest(UUID.randomUUID(), BigDecimal.TEN, "BRL", "acc-1", "acc-2", "order-1");
    }

    @Test
    void mapsApprovedResponseFromFeignClient() {
        adapter = new PaymentGatewayAdapter(feignClient);
        when(feignClient.process(any(PaymentGatewayRequestDto.class)))
                .thenReturn(new PaymentGatewayResponseDto(PaymentGatewayOutcomeDto.APPROVED, null));

        PaymentGatewayResponse response = adapter.process(newRequest());

        assertThat(response.outcome()).isEqualTo(PaymentGatewayOutcome.APPROVED);
    }

    @Test
    void mapsRejectedResponseFromFeignClient() {
        adapter = new PaymentGatewayAdapter(feignClient);
        when(feignClient.process(any(PaymentGatewayRequestDto.class)))
                .thenReturn(new PaymentGatewayResponseDto(PaymentGatewayOutcomeDto.REJECTED, "reason"));

        PaymentGatewayResponse response = adapter.process(newRequest());

        assertThat(response.outcome()).isEqualTo(PaymentGatewayOutcome.REJECTED);
        assertThat(response.reason()).isEqualTo("reason");
    }

    @Test
    void translatesFeignExceptionIntoPaymentGatewayUnavailableException() {
        adapter = new PaymentGatewayAdapter(feignClient);
        Request feignRequest = Request.create(Request.HttpMethod.POST, "/process", java.util.Map.of(), null,
                StandardCharsets.UTF_8, new RequestTemplate());
        when(feignClient.process(any(PaymentGatewayRequestDto.class)))
                .thenThrow(new FeignException.ServiceUnavailable("unavailable", feignRequest, null, null));

        assertThatThrownBy(() -> adapter.process(newRequest()))
                .isInstanceOf(PaymentGatewayUnavailableException.class);
    }
}
