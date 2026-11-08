package com.matessa.commercial.config;

import com.matessa.commercial.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Catalog reads are public (GET); catalog writes (POST/PUT/PATCH/DELETE) require MANAGER
 * or ADMIN. The token is re-validated here (defence in depth) even though the gateway
 * already checked it.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info", "/error").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // stored product images (WebP) are public assets
                        .requestMatchers(HttpMethod.GET, "/media/**").permitAll()
                        // public catalog reads
                        .requestMatchers(HttpMethod.GET,
                                "/api/categories/**", "/api/products/**", "/api/variants/**",
                                "/api/reviews/**", "/api/discounts/**").permitAll()
                        // wishlist is private to the authenticated owner (every method)
                        .requestMatchers("/api/wishlist/**").authenticated()
                        // any logged-in customer may write/edit/delete their own review
                        .requestMatchers(HttpMethod.POST, "/api/products/*/reviews").authenticated()
                        .requestMatchers("/api/reviews/**").authenticated()
                        // coupon validation is a stateless checkout preview (never redeems the
                        // code) — public so guests get live discount calculation too
                        .requestMatchers(HttpMethod.POST, "/api/coupons/validate").permitAll()
                        // public storefront submissions: contact form + newsletter signup (guests)
                        .requestMatchers(HttpMethod.POST, "/api/contact", "/api/subscribers").permitAll()
                        // Service-to-service checkout reservation. permitAll here because guest
                        // checkout carries no JWT, so there is no caller identity to require.
                        // Kept off the internet by the gateway (which denies /internal/** and has
                        // no route for it) plus docker-compose.prod.yml not publishing this port.
                        // See InternalReservationController for why that is the current answer.
                        .requestMatchers("/internal/**").permitAll()
                        // everything else — catalog writes, contact/subscriber reads, coupon CRUD — staff only
                        .anyRequest().hasAnyRole("MANAGER", "ADMIN"))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
