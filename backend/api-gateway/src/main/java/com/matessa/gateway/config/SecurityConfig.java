package com.matessa.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

/**
 * Edge security: validate the RS256 JWT (signature + expiry) once at the gateway and
 * reject junk early. Public auth endpoints pass through without a token; every other
 * route requires a valid token. Services still re-validate (defence in depth).
 * The gateway holds only the PUBLIC key — never the signing key.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories/**", "/api/products/**", "/api/variants/**",
                                "/api/reviews/**", "/api/discounts/**").permitAll() // public catalog reads
                        .requestMatchers(HttpMethod.GET, "/media/**").permitAll() // public product images (WebP)
                        // guest shopping: price a cart, preview a coupon, check out, and track without an account
                        .requestMatchers(HttpMethod.POST, "/api/cart/price", "/api/coupons/validate", "/api/orders/guest").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/orders/track").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/error", "/fallback/**").permitAll() // surface real errors / fallbacks, not a masked 401
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${jwt.public-key-path}") String publicKeyLocation,
                                 @Value("${jwt.issuer}") String issuer) throws IOException {
        try (InputStream is = openKey(publicKeyLocation)) {
            RSAPublicKey rsaPublicKey = RsaKeyConverters.x509().convert(is);
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(rsaPublicKey).build();
            // Validate iss in addition to signature + timestamps; see the services' JwtKeyConfig.
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
            return decoder;
        }
    }

    /**
     * CORS is normally a no-op here: the browser calls the Next.js BFF (same origin) and only
     * the server calls the gateway. It stays configurable so a direct browser→gateway origin
     * (if ever exposed) is correct per environment. Set CORS_ALLOWED_ORIGINS (comma-separated)
     * in production; defaults to the local Next dev origin (:3100).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${CORS_ALLOWED_ORIGINS:http://localhost:3100,http://localhost:3000}") String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split("\\s*,\\s*")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Resolves the key file from the configured location, falling back to common local-run
     * locations so a direct/IDE run works regardless of the working directory. Docker and
     * tests supply an absolute/explicit path, which is used as-is.
     */
    private static InputStream openKey(String location) throws IOException {
        Resource resource = new DefaultResourceLoader().getResource(location);
        if (resource.exists()) {
            return resource.getInputStream();
        }
        String filename = location.replaceAll(".*[/\\\\]", "");
        for (Path candidate : List.of(Path.of("keys", filename), Path.of("..", "..", "keys", filename))) {
            if (Files.exists(candidate)) {
                return Files.newInputStream(candidate);
            }
        }
        throw new FileNotFoundException("JWT key not found. Tried '" + location + "', './keys/" + filename
                + "' and '../../keys/" + filename + "'. Set JWT_PUBLIC_KEY_PATH or run from the module directory.");
    }
}
