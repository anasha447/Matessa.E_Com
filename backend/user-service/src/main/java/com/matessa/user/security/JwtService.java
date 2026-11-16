package com.matessa.user.security;

import com.matessa.user.tenant.TenantContext;

import com.matessa.user.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final long accessTokenTtlSeconds;
    private final String issuer;

    public JwtService(JwtEncoder encoder,
                      @Value("${jwt.access-token-ttl-seconds}") long accessTokenTtlSeconds,
                      @Value("${jwt.issuer}") String issuer) {
        this.encoder = encoder;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.issuer = issuer;
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        // While a temporary password is still in place the token carries NO roles, so every
        // service refuses staff endpoints on its own (role checks) until the password is changed.
        // Only "who am I" and "change my password" work, which need authentication alone.
        List<String> roles = user.isMustChangePassword()
                ? List.of()
                : user.getRoles().stream().map(Enum::name).toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(accessTokenTtlSeconds))
                .subject(String.valueOf(user.getId()))
                .claim("roles", roles)
                .claim("pwd_change", user.isMustChangePassword())
                .claim(TenantContext.CLAIM, requireTenant())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static String requireTenant() {
        String tenant = TenantContext.getTenantId();
        if (tenant == null) {
            throw new IllegalStateException("Cannot issue a token outside a store context");
        }
        return tenant;
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }
}
