package com.kavindu.caresummaryapi.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Who can do what. The same rules work for both login modes (app.security.mode):
 *  - keycloak (default): Keycloak logs users in and signs the tokens (RS256). See KeycloakConfig.
 *  - local            : the old built-in demo login; our API signs the tokens (HS256). See LocalLoginConfig.
 * In both modes the API is an OAuth 2.0 "resource server": it checks the token in
 * "Authorization: Bearer ..." on every request. In keycloak mode it never sees a password.
 *
 *  - GET  /api/auth/config        : anyone (tells the React app which login to show)
 *  - POST /api/auth/login         : anyone (local mode only; this is where you get a token)
 *  - /api/patients/**, /api/ai/** : DOCTOR only (patient data)
 *  - /api/audit/**                : ADMIN only (the admin sees the log, not patient records)
 *  - GET /actuator/health         : anyone (only UP/DOWN); other /actuator/** (metrics): ADMIN only
 *  - anything else                : any valid token
 */
@Configuration
public class SecurityConfig {

    /** Property that picks the login mode: "keycloak" (default) or "local". */
    public static final String MODE = "app.security.mode";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationConverter jwtConverter) throws Exception {
        http
                // No cookies/sessions: the token is sent by the client on every request, so CSRF does not apply
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/auth/config").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Health check is open (load balancers and uptime monitors call it without a login).
                        // It shows only UP/DOWN; the details are for ADMIN (see application.properties).
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/api/audit/**").hasRole("ADMIN")
                        .requestMatchers("/api/patients/**", "/api/ai/**").hasRole("DOCTOR")
                        .anyRequest().authenticated())
                // jwtConverter comes from KeycloakConfig or LocalLoginConfig: it turns a checked token into a user + roles
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)));
        return http.build();
    }

    /** Lets the React app (another port in development) call this API. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.security.allowed-origin}") String origin) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(origin));
        cors.setAllowedMethods(List.of("GET", "POST"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
