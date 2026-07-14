package com.matessa.user.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

/**
 * Loads the RS256 key pair. The private key signs tokens (this service only);
 * the public key validates them. Keys are supplied via env-configured paths and
 * never committed.
 */
@Configuration
public class JwtKeyConfig {

    @Bean
    public RSAPublicKey rsaPublicKey(@Value("${jwt.public-key-path}") String publicKeyLocation) throws IOException {
        try (InputStream is = open(publicKeyLocation)) {
            return RsaKeyConverters.x509().convert(is);
        }
    }

    @Bean
    public RSAPrivateKey rsaPrivateKey(@Value("${jwt.private-key-path}") String privateKeyLocation) throws IOException {
        try (InputStream is = open(privateKeyLocation)) {
            return RsaKeyConverters.pkcs8().convert(is);
        }
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        RSAKey jwk = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(jwk));
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAPublicKey publicKey) {
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }

    /**
     * Resolves a key file from the configured location, falling back to common local-run
     * locations so a direct/IDE run works regardless of the working directory. Docker and
     * tests supply an absolute/explicit path, which is used as-is.
     */
    private static InputStream open(String location) throws IOException {
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
                + "' and '../../keys/" + filename + "'. Set JWT_PUBLIC_KEY_PATH / JWT_PRIVATE_KEY_PATH"
                + " or run from the module directory.");
    }
}
