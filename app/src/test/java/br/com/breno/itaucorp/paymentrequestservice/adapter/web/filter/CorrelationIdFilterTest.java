package br.com.breno.itaucorp.paymentrequestservice.adapter.web.filter;

import static br.com.breno.itaucorp.paymentrequestservice.observability.CorrelationIdContext.HTTP_HEADER;
import static br.com.breno.itaucorp.paymentrequestservice.observability.CorrelationIdContext.MDC_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generatesCorrelationIdWhenHeaderIsAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> assertThat(MDC.get(MDC_KEY)).isNotBlank();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(HTTP_HEADER)).isNotBlank();
        assertThat(MDC.get(MDC_KEY)).isNull();
    }

    @Test
    void reusesCorrelationIdFromIncomingHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HTTP_HEADER, "existing-correlation-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> assertThat(MDC.get(MDC_KEY)).isEqualTo("existing-correlation-id");

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(HTTP_HEADER)).isEqualTo("existing-correlation-id");
        assertThat(MDC.get(MDC_KEY)).isNull();
    }

    @Test
    void clearsMdcEvenWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(MDC_KEY)).isNull();
    }
}
