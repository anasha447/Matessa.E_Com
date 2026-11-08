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

import java.io.IOException;
import java.time.Duration;
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

    private record Rule(String prefix, int limit, int windowSeconds) {}

    // Tightest limits on the most abusable public endpoints (per client, per window).
    // Longest prefix wins, so the list is ordered most-specific first.
    private static final List<Rule> RULES = List.of(
            new Rule("/api/auth/login", 10, 60),
            // Registration mints an account per call, and refresh takes an opaque token —
            // both were unthrottled while only /login was covered.
            new Rule("/api/auth/register", 5, 60),
            new Rule("/api/auth/refresh", 30, 60),
            new Rule("/api/orders/guest", 5, 60),
            new Rule("/api/orders/track", 20, 60),
            new Rule("/api/cart/price", 60, 60),
            // Public, and it answers whether a code is valid AND what it is worth, so it was
            // a free coupon-code oracle to brute-force.
            new Rule("/api/coupons/validate", 20, 60),
            // public storefront submissions — throttle bot spam (each subscribe also mints a coupon)
            new Rule("/api/contact", 20, 60),
            new Rule("/api/subscribers", 20, 60)
    );

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
        Rule rule = match(req.getRequestURI());
        if (rule != null && !allow(rule, clientIpResolver.resolve(req))) {
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

    /** Atomic INCR + first-hit EXPIRE = a fixed window. Fails open on Redis error. */
    private boolean allow(Rule rule, String ip) {
        long window = Instant.now().getEpochSecond() / rule.windowSeconds();
        String key = "rl:" + rule.prefix() + ':' + ip + ':' + window;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofSeconds(rule.windowSeconds()));
            }
            return count == null || count <= rule.limit();
        } catch (RuntimeException ex) {
            log.warn("Rate-limit check failed (Redis down?), failing open: {}", ex.toString());
            return true;
        }
    }

    /** Longest matching prefix wins, so a specific rule is never shadowed by a broader one. */
    private Rule match(String uri) {
        Rule best = null;
        for (Rule r : RULES) {
            if (uri.startsWith(r.prefix()) && (best == null || r.prefix().length() > best.prefix().length())) {
                best = r;
            }
        }
        return best;
    }
}
