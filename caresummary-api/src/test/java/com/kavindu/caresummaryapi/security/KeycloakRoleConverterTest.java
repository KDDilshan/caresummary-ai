package com.kavindu.caresummaryapi.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRoleConverterTest {

    private final KeycloakRoleConverter converter = new KeycloakRoleConverter();

    private static Jwt token(Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256").subject("user-id")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        claims.forEach(b::claim);
        return b.build();
    }

    private List<String> roles(Jwt jwt) {
        return converter.convert(jwt).stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void keepsOnlyAppRoles_andAddsRolePrefix() {
        Jwt jwt = token(Map.of("realm_access",
                Map.of("roles", List.of("offline_access", "DOCTOR", "default-roles-caresummary", "uma_authorization"))));

        assertThat(roles(jwt)).containsExactly("ROLE_DOCTOR");
    }

    @Test
    void userWithBothRoles_getsBoth() {
        Jwt jwt = token(Map.of("realm_access", Map.of("roles", List.of("DOCTOR", "ADMIN"))));

        assertThat(roles(jwt)).containsExactly("ROLE_ADMIN", "ROLE_DOCTOR");
    }

    @Test
    void noRealmAccessClaim_meansNoRoles() {
        assertThat(roles(token(Map.of("scope", "openid")))).isEmpty();
        assertThat(roles(token(Map.of("realm_access", Map.of())))).isEmpty();
    }

    @Test
    void roleNamesAreCaseSensitive() {
        // "doctor" is not "DOCTOR": a role must match exactly, no guessing
        Jwt jwt = token(Map.of("realm_access", Map.of("roles", List.of("doctor", "Admin"))));

        assertThat(roles(jwt)).isEmpty();
    }
}
