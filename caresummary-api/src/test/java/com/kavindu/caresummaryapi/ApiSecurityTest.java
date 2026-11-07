package com.kavindu.caresummaryapi;

import com.kavindu.caresummaryapi.ai.AiService;
import com.kavindu.caresummaryapi.ai.dto.AiResponse;
import com.kavindu.caresummaryapi.audit.AuditEvent.Action;
import com.kavindu.caresummaryapi.audit.AuditRepository;
import com.kavindu.caresummaryapi.patient.PatientNotFoundException;
import com.kavindu.caresummaryapi.patient.PatientService;
import com.kavindu.caresummaryapi.patient.dto.PatientDetail;
import com.kavindu.caresummaryapi.patient.dto.PatientSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full app (real security, real controllers, H2 database), but the FHIR server and the AI are mocked.
 * Checks login, roles, validation, errors and the audit log.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired AuditRepository auditRepo;

    @MockitoBean PatientService patients;
    @MockitoBean AiService ai;

    private final PatientSummary agnes = new PatientSummary("1413", "Agnes Treutel", "female", "1986-05-02", 39, false);

    @BeforeEach
    void setUp() {
        auditRepo.deleteAll();
        when(patients.listPatients(any())).thenReturn(List.of(agnes));
        when(patients.getPatient("1413")).thenReturn(new PatientDetail(agnes, List.of(), List.of(), List.of()));
        when(patients.getPatient("999")).thenThrow(new PatientNotFoundException("999"));
    }

    private String login(String user, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"value\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        assertThat(m.find()).as("token in response").isTrue();
        return "Bearer " + m.group(1);
    }

    @Test
    void loginReturnsOnlyRealRoles() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"doctor\",\"password\":\"doctor-test-pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("DOCTOR"));
    }

    @Test
    void noToken_is401() throws Exception {
        mvc.perform(get("/api/patients")).andExpect(status().isUnauthorized());
    }

    @Test
    void fakeToken_is401() throws Exception {
        mvc.perform(get("/api/patients").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPassword_is401_andIsAudited() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"doctor\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Wrong username or password"));

        assertThat(auditRepo.findAll()).anyMatch(e -> e.getAction() == Action.LOGIN_FAILED);
    }

    @Test
    void doctor_canSeePatients_andViewIsAudited() throws Exception {
        String token = login("doctor", "doctor-test-pw");

        mvc.perform(get("/api/patients").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Agnes Treutel"));
        mvc.perform(get("/api/patients/1413").header("Authorization", token))
                .andExpect(status().isOk());

        assertThat(auditRepo.findAll())
                .anyMatch(e -> e.getAction() == Action.VIEW_PATIENT
                        && "doctor".equals(e.getUsername()) && "1413".equals(e.getPatientId()));
    }

    @Test
    void doctor_cannotSeeAuditLog() throws Exception {
        mvc.perform(get("/api/audit").header("Authorization", login("doctor", "doctor-test-pw")))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_canSeeAuditLog_butNotPatients() throws Exception {
        String token = login("admin", "admin-test-pw");

        mvc.perform(get("/api/audit").header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(get("/api/patients").header("Authorization", token)).andExpect(status().isForbidden());
    }

    @Test
    void badIdIs400_unknownPatientIs404() throws Exception {
        String token = login("doctor", "doctor-test-pw");

        mvc.perform(get("/api/patients/bad id!").header("Authorization", token))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/patients/999").header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Patient not found: 999"));
    }

    @Test
    void aiSummary_returnsCheckedLines_andIsAudited() throws Exception {
        AiResponse fake = new AiResponse("1413", "summary", null,
                List.of(new AiResponse.CheckedLine("Has hypertension.", List.of("Condition/10"), true)),
                0, "test-model", Instant.now(), "AI draft");
        when(ai.summarize(any(), anyBoolean())).thenReturn(fake);

        mvc.perform(post("/api/patients/1413/summary").header("Authorization", login("doctor", "doctor-test-pw")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].verified").value(true))
                .andExpect(jsonPath("$.lines[0].sources[0]").value("Condition/10"));

        assertThat(auditRepo.findAll()).anyMatch(e -> e.getAction() == Action.AI_SUMMARY);
    }

    @Test
    void emptyOrTooLongQuestion_is400() throws Exception {
        String token = login("doctor", "doctor-test-pw");
        when(ai.answer(any(), anyString())).thenReturn(null);

        mvc.perform(post("/api/patients/1413/ask").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/patients/1413/ask").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"" + "a".repeat(301) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Question is too long (max 300 characters)"));
    }

    @Test
    void aiQuestion_passesTrimmedQuestion() throws Exception {
        AiResponse fake = new AiResponse("1413", "answer", "Any allergies?", List.of(), 0, "m", Instant.now(), "d");
        when(ai.answer(any(), eq("Any allergies?"))).thenReturn(fake);

        mvc.perform(post("/api/patients/1413/ask").header("Authorization", login("doctor", "doctor-test-pw"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"  Any allergies?  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("answer"));
    }
}
