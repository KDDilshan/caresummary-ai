package com.kavindu.caresummaryapi.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the user's roles from a Keycloak token.
 * Keycloak puts realm roles inside a nested claim:
 *     "realm_access": { "roles": ["DOCTOR", "default-roles-caresummary", "offline_access"] }
 * We keep only the roles this app knows (DOCTOR, ADMIN) and turn them into ROLE_DOCTOR / ROLE_ADMIN,
 * which is what hasRole("DOCTOR") in SecurityConfig looks for. Keycloak's own built-in roles are ignored.
 */
public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    static final Set<String> APP_ROLES = Set.of("DOCTOR", "ADMIN");

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();   // no roles = logged in, but allowed to do nothing that needs a role
        }
        return roles.stream()
                .map(String::valueOf)
                .filter(APP_ROLES::contains)
                .sorted()
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}
