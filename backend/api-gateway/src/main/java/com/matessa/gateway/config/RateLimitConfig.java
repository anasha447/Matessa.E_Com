package com.matessa.gateway.config;

import com.matessa.gateway.ratelimit.ClientIpResolver;
import com.matessa.gateway.ratelimit.RateLimitFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

/** Registers the edge rate limiter ahead of security + gateway routing. */
@Configuration
public class RateLimitConfig {

    /**
     * Networks whose {@code X-Forwarded-For} header is believed. Defaults to loopback plus the
     * private ranges Docker bridges live in, which covers the storefront BFF containers.
     * Narrow this in production to the actual reverse-proxy addresses: every network listed
     * here is a network that can claim to be any client.
     */
    @Bean
    public ClientIpResolver clientIpResolver(
            @Value("${app.ratelimit.trusted-proxies}") List<String> trustedProxies) {
        return new ClientIpResolver(trustedProxies);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(StringRedisTemplate redis,
                                                                   ClientIpResolver clientIpResolver) {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(redis, clientIpResolver));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);   // before Spring Security + routing
        return registration;
    }
}
