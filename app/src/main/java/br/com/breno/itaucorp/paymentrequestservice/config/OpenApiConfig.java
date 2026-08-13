package br.com.breno.itaucorp.paymentrequestservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Payment Request Service",
        description = "Serviço para registrar, consultar e acompanhar solicitações de pagamento (desafio Jornada de Pagamentos).",
        version = "v1"
))
public class OpenApiConfig {
}
