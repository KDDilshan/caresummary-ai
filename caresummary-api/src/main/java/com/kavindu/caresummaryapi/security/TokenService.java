package com.kavindu.caresummaryapi.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Makes a signed JWT after a successful login. */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final long minutes;

    public TokenService(JwtEncoder encoder, @Value("${app.security.token-minutes:60}") long minutes) {
        this.encoder = encoder;
        this.minutes = minutes;
    }

    public record Token(String value, List<String> roles, Instant expiresAt) {
    }

    public Token create(Authentication auth) {
        Instant now = Instant.now();
        Instant expires = now.plus(minutes, ChronoUnit.MINUTES);
        // Keep only real roles (ROLE_DOCTOR -> DOCTOR). Spring Security 7 also adds markers like
        // FACTOR_PASSWORD ("logged in with a password"), which are not roles.
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("caresummary-api")
                .subject(auth.getName())     // who
                .issuedAt(now)
                .expiresAt(expires)          // tokens stop working after this time
                .claim("roles", roles)       // what they are allowed to do
                .build();

        String value = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new Token(value, roles, expires);
    }
}
