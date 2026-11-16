package com.matessa.commercial.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Service-to-service authentication for {@code /internal/**} (checkout reservations).
 *
 * <p>These calls carry no user token — guest checkout has none — so they used to rely on the
 * network alone: any container on the Docker network could take stock off the shelf or claim
 * coupon uses. Now the caller must present {@code X-Internal-Key}, the shared secret only
 * order-service is given ({@code INTERNAL_API_KEY}). With no key configured, every internal
 * call is refused.
 */
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Key";

    private final byte[] expected;

    public InternalApiKeyFilter(String apiKey) {
        this.expected = apiKey == null || apiKey.isBlank() ? null : apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        boolean ok = expected != null && presented != null
                && MessageDigest.isEqual(expected, presented.getBytes(StandardCharsets.UTF_8)); // constant time
        if (!ok) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Internal endpoint\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
