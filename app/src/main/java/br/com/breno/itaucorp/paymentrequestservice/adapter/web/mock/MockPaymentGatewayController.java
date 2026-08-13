package br.com.breno.itaucorp.paymentrequestservice.adapter.web.mock;

import br.com.breno.itaucorp.paymentrequestservice.adapter.external.gateway.PaymentGatewayOutcomeDto;
import br.com.breno.itaucorp.paymentrequestservice.adapter.external.gateway.PaymentGatewayRequestDto;
import br.com.breno.itaucorp.paymentrequestservice.adapter.external.gateway.PaymentGatewayResponseDto;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulação local de um gateway de pagamento externo (ver ADR-007). O desfecho é determinístico,
 * derivado do próprio payload da requisição, para permitir demonstrar os três resultados possíveis:
 * <ul>
 *     <li>{@code context} contendo "unavailable" (case-insensitive) → HTTP 503, simula falha técnica (aciona retry/FAILED)</li>
 *     <li>{@code amount >= 10000} → REJECTED, "amount exceeds gateway approval limit"</li>
 *     <li>{@code origin} igual a {@code destination} → REJECTED, "origin and destination accounts must differ"</li>
 *     <li>caso contrário → APPROVED</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/mock/payment-gateway")
public class MockPaymentGatewayController {

    private static final BigDecimal APPROVAL_LIMIT = BigDecimal.valueOf(10_000);

    @PostMapping("/process")
    public ResponseEntity<PaymentGatewayResponseDto> process(@RequestBody PaymentGatewayRequestDto request) {
        log.info("Mock gateway recebeu solicitação: paymentRequestId={}", request.paymentRequestId());

        if (request.context() != null && request.context().toLowerCase().contains("unavailable")) {
            log.warn("Mock gateway simulando indisponibilidade: paymentRequestId={}", request.paymentRequestId());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        if (request.amount().compareTo(APPROVAL_LIMIT) >= 0) {
            return ResponseEntity.ok(new PaymentGatewayResponseDto(
                    PaymentGatewayOutcomeDto.REJECTED, "amount exceeds gateway approval limit"));
        }

        if (request.origin().equalsIgnoreCase(request.destination())) {
            return ResponseEntity.ok(new PaymentGatewayResponseDto(
                    PaymentGatewayOutcomeDto.REJECTED, "origin and destination accounts must differ"));
        }

        return ResponseEntity.ok(new PaymentGatewayResponseDto(PaymentGatewayOutcomeDto.APPROVED, null));
    }
}
