package com.knowledgegraph.core.unit;

import static org.mockito.Mockito.*;

import com.knowledgegraph.core.config.ApiKeyAuthFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiKeyAuthFilterTest {

    private static final String CONFIGURED_KEY = "test-key";

    private ApiKeyAuthFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthFilter(CONFIGURED_KEY);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/entities");
    }

    @Test
    void allowsRequestWithValidApiKey() throws Exception {
        when(request.getHeader("X-API-Key")).thenReturn(CONFIGURED_KEY);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void rejectsRequestWithMissingApiKey() throws Exception {
        when(request.getHeader("X-API-Key")).thenReturn(null);
        when(response.getWriter()).thenReturn(mock(PrintWriter.class));

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(response).setStatus(401);
    }

    @Test
    void rejectsRequestWithInvalidApiKey() throws Exception {
        when(request.getHeader("X-API-Key")).thenReturn("wrong-key");
        when(response.getWriter()).thenReturn(mock(PrintWriter.class));

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(response).setStatus(401);
    }

    @Test
    void bypassesFilterForNonGraphPaths() throws Exception {
        when(request.getRequestURI()).thenReturn("/swagger-ui.html");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
