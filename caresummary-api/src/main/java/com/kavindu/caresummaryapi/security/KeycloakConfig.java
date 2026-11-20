package com.kavindu.caresummaryapi.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.Collection;

/**
 * Keycloak mode (the default). Keycloak is the "authorization server": it shows the login page,
 * checks the password and gives the React app a signed access token. Our API is the "resource server":
 * for every request it checks that token. A token is accepted only if ALL of these are true:
 *   1. Signature: signed (RS256) by one of Keycloak's private keys. We check it with Keycloak's PUBLIC keys,
 *      downloaded from its JWKS address and cached. So nobody can make a token without Keycloak.
 *   2. "iss" (issuer) is exactly our realm, e.g. http://localhost:8180/realms/caresummary.
 *   3. "exp" / "nbf": not expired and already valid (60 s clock skew allowed).
 *   4. "aud" (audience) contains "caresummary-api": the token was made FOR this API.
 *      This stops a token meant for another app (or an ID token) from being used here.
 */
@Configuration
@ConditionalOnProperty(name = SecurityConfig.MODE, havingValue = "keycloak", matchIfMissing = true)
public class KeycloakConfig {

    @Bean
    public JwtDecoder jwtDecoder(@Value("${app.security.keycloak.issuer-uri}") String issuer,
                                 @Value("${app.security.keycloak.audience}") String audience) {
        // Keycloak publishes its public keys here. They are fetched on the first request (not at startup),
        // so the API still starts when Keycloak is down; requests just get 401 until it is back.
        String jwksUri = issuer + "/protocol/openid-connect/certs";
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)   // refuse other algorithms (e.g. "none" or HS256)
                .build();
        decoder.setJwtValidator(tokenValidator(issuer, audience));
        return decoder;
    }

    /** Checks 2-4 from the list above. Static so the unit test can use exactly the same rules. */
    static OAuth2TokenValidator<Jwt> tokenValidator(String issuer, String audience) {
        OAuth2TokenValidator<Jwt> issuerAndTime = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> audienceCheck = new JwtClaimValidator<Collection<String>>(
                JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience));
        return new DelegatingOAuth2TokenValidator<>(issuerAndTime, audienceCheck);
    }

    /**
     * Turns a checked token into a Spring Security user:
     *  - name  = "preferred_username" (e.g. "doctor"), not "sub" (a random-looking Keycloak user id).
     *    This is the name written to the audit log.
     *  - roles = realm roles from "realm_access.roles" (see KeycloakRoleConverter).
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }
}
