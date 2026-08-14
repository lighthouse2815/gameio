package com.gameio.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gameio.observability.ObservabilityProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MetricsTokenFilterTest {
    private static final String TOKEN = "test-metrics-token-with-more-than-thirty-two-characters";
    private final MetricsTokenFilter filter = new MetricsTokenFilter(new ObservabilityProperties(TOKEN));

    @Test
    void rejectsMissingMetricsTokenWithoutCallingTheChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INVALID_METRICS_TOKEN");
        verifyNoInteractions(chain);
    }

    @Test
    void acceptsExactMetricsHeaderAndIgnoresOtherPaths() throws Exception {
        MockHttpServletRequest metricsRequest = new MockHttpServletRequest("GET", "/actuator/prometheus");
        metricsRequest.addHeader(MetricsTokenFilter.HEADER_NAME, TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(metricsRequest, response, chain);
        verify(chain).doFilter(metricsRequest, response);

        MockHttpServletRequest healthRequest = new MockHttpServletRequest("GET", "/actuator/health");
        FilterChain healthChain = mock(FilterChain.class);
        filter.doFilter(healthRequest, new MockHttpServletResponse(), healthChain);
        verify(healthChain).doFilter(
                org.mockito.ArgumentMatchers.eq(healthRequest), org.mockito.ArgumentMatchers.any());
    }
}
