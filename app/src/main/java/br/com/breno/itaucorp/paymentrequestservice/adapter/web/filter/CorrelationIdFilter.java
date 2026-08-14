package br.com.breno.itaucorp.paymentrequestservice.adapter.web.filter;

import static br.com.breno.itaucorp.paymentrequestservice.observability.CorrelationIdContext.HTTP_HEADER;
import static br.com.breno.itaucorp.paymentrequestservice.observability.CorrelationIdContext.MDC_KEY;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Garante que toda requisição HTTP tenha um correlationId (recebido via header ou gerado),
 * disponível no MDC para o padrão de log configurado e devolvido ao cliente na resposta.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = StringUtils.hasText(request.getHeader(HTTP_HEADER))
                ? request.getHeader(HTTP_HEADER)
                : UUID.randomUUID().toString();

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HTTP_HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
