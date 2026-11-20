package com.kavindu.caresummaryapi.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks the token rules from KeycloakConfig with real signed tokens.
 * We play Keycloak: make an RSA key pair, sign tokens with the private key, and check them with the
 * public key plus the same validator the app uses (issuer, expiry, audience).
 */
class KeycloakTokenValidationTest {

    static final String ISSUER = "http://localhost:8180/realms/caresummary";
    static final String AUDIENCE = "caresummary-api";

    static RSAKey keycloakKey;   // the "real" Keycloak key
    static RSAKey attackerKey;   // some other key
    static NimbusJwtDecoder decoder;

    @BeforeAll
    static void setUp() throws Exception {
        keycloakKey = new RSAKeyGenerator(2048).keyID("kc-1").generate();
        attackerKey = new RSAKeyGenerator(2048).keyID("kc-1").generate();   // same key id, different key
        decoder = NimbusJwtDecoder.withPublicKey(keycloakKey.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(KeycloakConfig.tokenValidator(ISSUER, AUDIENCE));
    }

    /** A token like the ones Keycloak gives the React app. Change one thing with "tweak". */
    private static String token(RSAKey signingKey, Consumer<JwtClaimsSet.Builder> tweak) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject("8c1f0a2e-user-id")
                .audience(List.of(AUDIENCE, "account"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("azp", "caresummary-ui")
                .claim("preferred_username", "doctor")
                .claim("realm_access", Map.of("roles", List.of("DOCTOR")));
        tweak.accept(claims);
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(signingKey.getKeyID()).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    @Test
    void goodToken_isAccepted() {
        Jwt jwt = decoder.decode(token(keycloakKey, c -> { }));

        assertThat(jwt.getClaimAsString("preferred_username")).isEqualTo("doctor");
        assertThat(jwt.getAudience()).contains(AUDIENCE);
    }

    @Test
    void tokenSignedWithAnotherKey_isRejected() {
        assertThatThrownBy(() -> decoder.decode(token(attackerKey, c -> { })))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenFromAnotherRealm_isRejected() {
        String otherRealm = token(keycloakKey, c -> c.issuer("http://localhost:8180/realms/other"));

        assertThatThrownBy(() -> decoder.decode(otherRealm))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("iss");
    }

    @Test
    void tokenForAnotherApp_isRejected() {
        // e.g. an ID token: its audience is the UI client, not our API
        String idToken = token(keycloakKey, c -> c.audience(List.of("caresummary-ui")));

        assertThatThrownBy(() -> decoder.decode(idToken))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("aud");
    }

    @Test
    void tokenWithNoAudience_isRejected() {
        String noAud = token(keycloakKey, c -> c.claims(m -> m.remove("aud")));

        assertThatThrownBy(() -> decoder.decode(noAud)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void expiredToken_isRejected() {
        Instant past = Instant.now().minusSeconds(600);   // 10 minutes ago (more than the 60 s clock skew)
        String expired = token(keycloakKey, c -> c.issuedAt(past.minusSeconds(300)).expiresAt(past));

        assertThatThrownBy(() -> decoder.decode(expired))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("expired");
    }
}
