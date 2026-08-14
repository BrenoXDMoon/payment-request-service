package br.com.breno.itaucorp.paymentrequestservice.observability;

public final class CorrelationIdContext {

    public static final String MDC_KEY = "correlationId";
    public static final String HTTP_HEADER = "X-Correlation-Id";
    public static final String KAFKA_HEADER = "correlationId";

    private CorrelationIdContext() {
    }
}
