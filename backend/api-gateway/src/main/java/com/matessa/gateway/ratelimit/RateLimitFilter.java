package com.matessa.gateway.ratelimit;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Redis fixed-window rate limiter at the edge. Caps the abusable public endpoints
 * (login brute-force, guest-order / track / price abuse) per client IP. Runs before security
 * and gateway routing. <b>Fails open</b> if Redis is unavailable — a cache outage must never
 * take the whole gateway down.
 */
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /** {@code exactPost}: the rule covers only {@code POST <prefix>} exactly, not the subtree. */
    private record Rule(String prefix, int limit, int windowSeconds, boolean exactPost) {
        Rule(String prefix, int limit, int windowSeconds) {
            this(prefix, limit, windowSeconds, false);
        }
    }

    // Tightest limits on the most abusable public endpoints (per client, per window).
    // Longest prefix wins, so the list is ordered most-specific first.
    private static final List<Rule> RULES = List.of(
            new Rule("/api/auth/login", 10, 60),
            // Registration mints an account per call, and refresh takes an opaque token —
            // both were unthrottled while only /login was covered.
            new Rule("/api/auth/register", 5, 60),
            new Rule("/api/auth/refresh", 30, 60),
            new Rule("/api/orders/guest", 5, 60),
            // Signed-in checkout: one order per click is plenty; this caps scripted COD abuse.
            new Rule("/api/orders", 10, 60, true),
            new Rule("/api/orders/track", 20, 60),
            new Rule("/api/cart/price", 60, 60),
            // Public, and it answers whether a code is valid AND what it is worth, so it was
            // a free coupon-code oracle to brute-force.
            new Rule("/api/coupons/validate", 20, 60),
            // public storefront submissions — throttle bot spam (each subscribe also mints a coupon)
            new Rule("/api/contact", 20, 60),
            new Rule("/api/subscribers", 20, 60)
    );

    /**
     * INCR and the first-hit EXPIRE in one server-side step. As two separate calls, a failure
     * (or a timeout) between them left a counter with no TTL — a key that never expired and
     * so locked that client out of the endpoint permanently.
     */
    private static final RedisScript<Long> HIT = new DefaultRedisScript<>(
            "local n = redis.call('INCR', KEYS[1]) "
                    + "if n == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return n", Long.class);

    private final StringRedisTemplate redis;
    private final ClientIpResolver clientIpResolver;

    public RateLimitFilter(StringRedisTemplate redis, ClientIpResolver clientIpResolver) {
        this.redis = redis;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        Rule rule = match(req.getMethod(), req.getRequestURI());
        if (rule != null && !allow(rule, storeOf(req), clientIpResolver.resolve(req))) {
            HttpServletResponse res = (HttpServletResponse) response;
            res.setStatus(429);
            res.setHeader("Retry-After", String.valueOf(rule.windowSeconds()));
            res.setContentType("application/json");
            res.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\","
                    + "\"message\":\"Rate limit exceeded — slow down and retry shortly.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * Fixed window per store, per client. The store is part of the key so the two
     * storefronts never share (or exhaust) each other's budget. Fails open on Redis error.
     */
    private boolean allow(Rule rule, String store, String ip) {
        long window = Instant.now().getEpochSecond() / rule.windowSeconds();
        String key = "rl:" + rule.prefix() + ':' + store + ':' + ip + ':' + window;
        try {
            Long count = redis.execute(HIT, List.of(key), String.valueOf(rule.windowSeconds()));
            return count == null || count <= rule.limit();
        } catch (RuntimeException ex) {
            log.warn("Rate-limit check failed (Redis down?), failing open: {}", ex.toString());
            return true;
        }
    }

    /** The (unvalidated) store header, reduced to a safe key fragment; the guard validates it. */
    private static String storeOf(HttpServletRequest req) {
        String store = req.getHeader("X-Tenant-ID");
        if (store == null) {
            return "-";
        }
        String clean = store.replaceAll("[^a-z0-9_]", "");
        return clean.isEmpty() ? "-" : clean.substring(0, Math.min(clean.length(), 31));
    }

    /** Longest matching prefix wins, so a specific rule is never shadowed by a broader one. */
    private Rule match(String method, String uri) {
        Rule best = null;
        for (Rule r : RULES) {
            boolean hit = r.exactPost()
                    ? "POST".equals(method) && uri.equals(r.prefix())
                    : uri.startsWith(r.prefix());
            if (hit && (best == null || r.prefix().length() > best.prefix().length())) {
                best = r;
            }
        }
        return best;
    }
}
