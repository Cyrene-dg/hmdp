package com.qinghe.marketing.shared.trace;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TraceIdFilterTest {

    @Test
    void shouldEchoSafeRequestIdAndClearThreadContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.REQUEST_HEADER, "request-20260908-0001");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] observed = new String[1];
        FilterChain chain = (req, res) -> observed[0] = MDC.get(TraceIdFilter.MDC_KEY);

        new TraceIdFilter().doFilter(request, response, chain);

        assertEquals("request-20260908-0001", observed[0]);
        assertEquals("request-20260908-0001", response.getHeader(TraceIdFilter.TRACE_HEADER));
        assertNull(MDC.get(TraceIdFilter.MDC_KEY));
    }
}
