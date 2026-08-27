package com.knowledgegraph.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects any request to a graph endpoint lacking a valid {@code X-API-Key} header (Constitution
 * Principle IV minimum interim auth bar, FR-016). OAuth2/JWT is a later, dedicated security slice.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String[] PROTECTED_PREFIXES = {
        "/entities", "/relationships", "/entity-types", "/relationship-types"
    };

    private final String configuredApiKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ApiKeyAuthFilter(@Value("${app.security.api-key}") String configuredApiKey) {
        this.configuredApiKey = configuredApiKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String prefix : PROTECTED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String providedKey = request.getHeader(API_KEY_HEADER);
        if (providedKey == null || !providedKey.equals(configuredApiKey)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter()
                    .write(objectMapper.writeValueAsString(Map.of(
                            "timestamp", Instant.now().toString(),
                            "status", HttpStatus.UNAUTHORIZED.value(),
                            "error", "Missing or invalid X-API-Key header")));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
