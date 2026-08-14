package br.com.breno.itaucorp.paymentrequestservice.adapter.web;

import br.com.breno.itaucorp.paymentrequestservice.adapter.web.dto.CreatePaymentRequestRequest;
import br.com.breno.itaucorp.paymentrequestservice.adapter.web.dto.PaymentRequestResponse;
import br.com.breno.itaucorp.paymentrequestservice.application.exception.PaymentRequestNotFoundException;
import br.com.breno.itaucorp.paymentrequestservice.application.port.in.CreatePaymentRequestCommand;
import br.com.breno.itaucorp.paymentrequestservice.application.port.in.CreatePaymentRequestUseCase;
import br.com.breno.itaucorp.paymentrequestservice.application.port.in.GetPaymentRequestUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payment Requests", description = "Criação e consulta de solicitações de pagamento")
@RestController
@RequestMapping("/payment-requests")
@RequiredArgsConstructor
public class PaymentRequestController {

    private final CreatePaymentRequestUseCase createPaymentRequestUseCase;
    private final GetPaymentRequestUseCase getPaymentRequestUseCase;

    @Operation(summary = "Cria uma nova solicitação de pagamento")
    @ApiResponse(responseCode = "201", description = "Solicitação criada")
    @PostMapping
    public ResponseEntity<PaymentRequestResponse> create(@Valid @RequestBody CreatePaymentRequestRequest request) {
        var command = new CreatePaymentRequestCommand(
                request.amount(),
                request.currency(),
                request.origin(),
                request.destination(),
                request.context());

        var paymentRequest = createPaymentRequestUseCase.create(command);

        var response = PaymentRequestResponse.from(paymentRequest);
        return ResponseEntity.created(URI.create("/payment-requests/" + response.id())).body(response);
    }

    @Operation(summary = "Consulta uma solicitação de pagamento por identificador")
    @ApiResponse(responseCode = "200", description = "Solicitação encontrada")
    @ApiResponse(responseCode = "404", description = "Solicitação não encontrada")
    @GetMapping("/{id}")
    public ResponseEntity<PaymentRequestResponse> findById(@PathVariable UUID id) {
        var paymentRequest = getPaymentRequestUseCase.findById(id)
                .orElseThrow(() -> new PaymentRequestNotFoundException(id));

        return ResponseEntity.ok(PaymentRequestResponse.from(paymentRequest));
    }
}
