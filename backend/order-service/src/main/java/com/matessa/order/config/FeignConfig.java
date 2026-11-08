package com.matessa.order.config;

import com.matessa.order.client.CommercialErrorDecoder;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Forwards the caller's Authorization header on outbound Feign calls, so authenticated
 * downstream endpoints (e.g. coupon validation) see the same user. Public endpoints
 * (price/stock) simply ignore it.
 */
@Configuration
public class FeignConfig {

    /**
     * Maps commercial-service's 409/400 responses back onto this service's own exceptions, so
     * an out-of-stock answer reaches the customer as a 409 rather than being flattened into a
     * 503 by the circuit-breaker fallback. See {@link CommercialErrorDecoder}.
     */
    @Bean
    public ErrorDecoder commercialErrorDecoder() {
        return new CommercialErrorDecoder();
    }

    @Bean
    public RequestInterceptor authForwardingInterceptor() {
        return template -> {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                String authorization = attributes.getRequest().getHeader("Authorization");
                if (authorization != null && !authorization.isBlank()) {
                    template.header("Authorization", authorization);
                }
            }
        };
    }
}
