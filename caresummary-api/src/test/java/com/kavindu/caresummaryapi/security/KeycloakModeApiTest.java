package com.kavindu.caresummaryapi.security;

import com.kavindu.caresummaryapi.ai.AiService;
import com.kavindu.caresummaryapi.audit.AuditEvent.Action;
import com.kavindu.caresummaryapi.audit.AuditRepository;
import com.kavindu.caresummaryapi.patient.PatientService;
import com.kavindu.caresummaryapi.patient.dto.PatientDetail;
import com.kavindu.caresummaryapi.patient.dto.PatientSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full app in keycloak mode. The token check itself (signature, iss, aud, exp) is tested in
 * KeycloakTokenValidationTest; here the decoder is mocked so we can test what happens AFTER a token
 * is accepted: user name, roles, access rules and the audit log.
 */
@SpringBootTest(properties = "app.security.mode=keycloak")
@AutoConfigureMockMvc
class KeycloakModeApiTest {

    @Autowired MockMvc mvc;
    @Autowired AuditRepository auditRepo;

    @MockitoBean JwtDecoder decoder;
    @MockitoBean PatientService patients;
    @MockitoBean AiService ai;

    private final PatientSummary agnes = new PatientSummary("1413", "Agnes Treutel", "female", "1986-05-02", 39, false);

    /** What Keycloak's token looks like after decoding: the user id is in "sub", the name in "preferred_username". */
    private static Jwt keycloakToken(String username, List<String> realmRoles) {
        return Jwt.withTokenValue(username + "-token").header("alg", "RS256")
                .issuer("http://localhost:8180/realms/caresummary")
                .subject("8c1f0a2e-" + username)
                .audience(List.of("caresummary-api"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .claim("preferred_username", username)
                .claim("realm_access", Map.of("roles", realmRoles))
                .build();
    }

    @BeforeEach
    void setUp() {
        auditRepo.deleteAll();
        when(decoder.decode("doctor-token")).thenReturn(keycloakToken("doctor", List.of("DOCTOR", "offline_access")));
        when(decoder.decode("admin-token")).thenReturn(keycloakToken("admin", List.of("ADMIN")));
        when(decoder.decode("nurse-token")).thenReturn(keycloakToken("nurse", List.of("default-roles-caresummary")));
        when(decoder.decode("bad-token")).thenThrow(new BadJwtException("Signed JWT rejected"));
        when(patients.listPatients(any())).thenReturn(List.of(agnes));
        when(patients.getPatient("1413")).thenReturn(new PatientDetail(agnes, List.of(), List.of(), List.of()));
    }

    @Test
    void config_isPublic_andPointsToKeycloak() throws Exception {
        mvc.perform(get("/api/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("keycloak"))
                .andExpect(jsonPath("$.issuer").value("http://localhost:8180/realms/caresummary"))
                .andExpect(jsonPath("$.clientId").value("caresummary-ui"));
    }

    @Test
    void me_showsUsernameAndAppRolesOnly() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer doctor-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("doctor"))
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("DOCTOR"));
    }

    @Test
    void doctor_canSeePatients_andAuditUsesUsernameNotUserId() throws Exception {
        mvc.perform(get("/api/patients/1413").header("Authorization", "Bearer doctor-token"))
                .andExpect(status().isOk());

        assertThat(auditRepo.findAll())
                .anyMatch(e -> e.getAction() == Action.VIEW_PATIENT && "doctor".equals(e.getUsername()));
    }

    @Test
    void admin_seesAuditLog_butNotPatients() throws Exception {
        mvc.perform(get("/api/audit").header("Authorization", "Bearer admin-token")).andExpect(status().isOk());
        mvc.perform(get("/api/patients").header("Authorization", "Bearer admin-token")).andExpect(status().isForbidden());
    }

    @Test
    void userWithNoAppRole_canLogIn_butIsForbiddenEverywhere() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer nurse-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(0));
        mvc.perform(get("/api/patients").header("Authorization", "Bearer nurse-token")).andExpect(status().isForbidden());
        mvc.perform(get("/api/audit").header("Authorization", "Bearer nurse-token")).andExpect(status().isForbidden());
    }

    @Test
    void rejectedToken_is401() throws Exception {
        mvc.perform(get("/api/patients").header("Authorization", "Bearer bad-token")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/patients")).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordLogin_doesNotExistInKeycloakMode() throws Exception {
        // Passwords go to Keycloak only. Our API has no login endpoint to attack.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"doctor\",\"password\":\"doctor123\"}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405));
    }
}
