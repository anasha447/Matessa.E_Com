package com.matessa.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

/**
 * Loads the RS256 PUBLIC key and builds the JWT decoder. This service validates tokens
 * but never signs them, so it holds the public key only.
 */
@Configuration
public class JwtKeyConfig {

    @Bean
    public JwtDecoder jwtDecoder(@Value("${jwt.public-key-path}") String publicKeyLocation) throws IOException {
        try (InputStream is = KeyFiles.open(publicKeyLocation)) {
            RSAPublicKey rsaPublicKey = RsaKeyConverters.x509().convert(is);
            return NimbusJwtDecoder.withPublicKey(rsaPublicKey).build();
        }
    }

    /**
     * Resolves a key file from the configured location, falling back to common local-run
     * locations so a direct/IDE run works regardless of the working directory. Docker and
     * tests supply an absolute/explicit path, which is used as-is.
     */
    static final class KeyFiles {
        private KeyFiles() {
        }

        static InputStream open(String location) throws IOException {
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
}
