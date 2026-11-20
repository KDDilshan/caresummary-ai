package com.kavindu.caresummaryapi.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 *   GET /api/auth/config -> which login the React app should show (anyone)
 *   GET /api/auth/me     -> who am I, as the API sees me (needs a token)
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final String mode;
    private final String issuer;
    private final String clientId;

    public AuthController(@Value("${" + SecurityConfig.MODE + ":keycloak}") String mode,
                          @Value("${app.security.keycloak.issuer-uri:}") String issuer,
                          @Value("${app.security.keycloak.ui-client-id:caresummary-ui}") String clientId) {
        this.mode = mode;
        this.issuer = issuer;
        this.clientId = clientId;
    }

    /** Public settings only (no secrets): the React app uses them to start the OpenID Connect login. */
    @GetMapping("/config")
    public Map<String, String> config() {
        if ("local".equals(mode)) {
            return Map.of("mode", "local");
        }
        return Map.of("mode", "keycloak", "issuer", issuer, "clientId", clientId);
    }

    /**
     * The UI calls this right after login. It shows the user and roles AFTER our API checked the token,
     * so the UI shows exactly what the API will allow (it does not trust its own reading of the token).
     */
    @GetMapping("/me")
    public Map<String, Object> me(Authentication user) {
        List<String> roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .sorted()
                .toList();
        return Map.of("username", user.getName(), "roles", roles);
    }
}
