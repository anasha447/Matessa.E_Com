package com.matessa.gateway.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The edge half of store isolation (ARCHITECTURE.md §8.1).
 *
 * <p>Every API call names exactly one store in {@value #HEADER}. The services already refuse a
 * missing or unknown store and a token from another store; doing the same here means a bad
 * request is turned away before it costs a hop, and — more importantly — that a token minted
 * for one store can never be replayed against the other at the edge either.
 *
 * <ul>
 *   <li>Missing or unknown store on an {@code /api/**} call → 400.</li>
 *   <li>Authenticated request whose token {@value #CLAIM} claim is a different store → 403.</li>
 * </ul>
 *
 * Runs inside the security chain right after bearer-token authentication, so the verified
 * JWT (signature, issuer, expiry already checked) is available. {@code /media/**} images are
 * public and store-agnostic by path, and actuator/fallback/error are infrastructure.
 */
public class StoreGuardFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Tenant-ID";
    public static final String CLAIM = "tenant";
    private static final Pattern SAFE_ID = Pattern.compile("[a-z][a-z0-9_]{1,30}");

    private final List<String> stores;

    public StoreGuardFilter(List<String> stores) {
        if (stores == null || stores.isEmpty()) {
            throw new IllegalStateException("app.tenancy.tenants must list at least one store id");
        }
        for (String store : stores) {
            if (!SAFE_ID.matcher(store).matches() || store.startsWith("pg_") || store.equals("public")
                    || store.equals("information_schema")) {
                throw new IllegalStateException("Invalid store id '" + store + "'");
            }
        }
        this.stores = List.copyOf(stores);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String store = request.getHeader(HEADER);
        if (store == null || !stores.contains(store)) {
            reject(request, response, HttpServletResponse.SC_BAD_REQUEST, "Bad Request",
                    "Missing or unknown store (" + HEADER + " header)");
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt
                && !Objects.equals(jwt.getToken().getClaimAsString(CLAIM), store)) {
            reject(request, response, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                    "This session belongs to another store");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Same body shape as the services' ErrorResponse DTO. */
    private static void reject(HttpServletRequest request, HttpServletResponse response, int status,
                               String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"timestamp\":\"" + Instant.now() + "\",\"status\":" + status
                + ",\"error\":\"" + error + "\",\"message\":\"" + message + "\",\"path\":\""
                + request.getRequestURI().replace("\"", "").replace("\\", "") + "\"}");
    }
}
