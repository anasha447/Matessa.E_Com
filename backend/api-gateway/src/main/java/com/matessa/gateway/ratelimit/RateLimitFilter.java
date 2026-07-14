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

    // Tightest limits on the most abusable public endpoints (per IP, per window).
    private static final List<Rule> RULES = List.of(
            new Rule("/api/auth/login", 10, 60),
            new Rule("/api/orders/guest", 5, 60),
            new Rule("/api/orders/track", 20, 60),
            new Rule("/api/cart/price", 60, 60),
            // public storefront submissions — throttle bot spam (each subscribe also mints a coupon)
            new Rule("/api/contact", 20, 60),
            new Rule("/api/subscribers", 20, 60)
    );

    private final StringRedisTemplate redis;

    public RateLimitFilter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        Rule rule = match(req.getRequestURI());
        if (rule != null && !allow(rule, clientIp(req))) {
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

    private Rule match(String uri) {
        for (Rule r : RULES) {
            if (uri.startsWith(r.prefix())) return r;
        }
        return null;
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
