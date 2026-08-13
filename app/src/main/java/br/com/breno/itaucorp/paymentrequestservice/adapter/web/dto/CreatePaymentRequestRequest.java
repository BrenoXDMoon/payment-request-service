package br.com.breno.itaucorp.paymentrequestservice.adapter.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record CreatePaymentRequestRequest(

        @NotNull
        @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        @Schema(example = "150.00")
        BigDecimal amount,

        @NotBlank
        @Pattern(regexp = "[A-Z]{3}", message = "currency must be a 3-letter ISO 4217 code")
        @Schema(example = "BRL")
        String currency,

        @NotBlank
        @Schema(example = "acc-1234")
        String origin,

        @NotBlank
        @Schema(example = "acc-5678")
        String destination,

        @NotBlank
        @Schema(example = "order-9012")
        String context
) {
}
