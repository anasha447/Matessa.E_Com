package com.matessa.order.tenant;

import java.io.IOException;
import java.time.Instant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds every API request to exactly one store. Runs before Spring Security so the JWT
 * filter can check the token's store claim against it. A missing or unknown store is a
 * 400 — there is deliberately no fallback, so a request can never land in the wrong store.
 */
public class TenantFilter extends OncePerRequestFilter {

    private final TenantProperties tenants;

    public TenantFilter(TenantProperties tenants) {
        this.tenants = tenants;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator")
                || uri.startsWith("/swagger-ui")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/media/")      // static, UUID-named product images
                || uri.equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String tenant = request.getHeader(TenantContext.HEADER);
        if (!tenants.isKnown(tenant)) {
            reject(request, response);
            return;
        }
        request.setAttribute(TenantContext.REQUEST_ATTRIBUTE, tenant);
        TenantContext.setTenantId(tenant);
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    /** Same body shape as the services' ErrorResponse DTO. */
    private static void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"timestamp\":\"" + Instant.now()
                + "\",\"status\":400,\"error\":\"Bad Request\","
                + "\"message\":\"Missing or unknown store (" + TenantContext.HEADER + " header)\","
                + "\"path\":\"" + request.getRequestURI().replace("\"", "") + "\"}");
    }
}
