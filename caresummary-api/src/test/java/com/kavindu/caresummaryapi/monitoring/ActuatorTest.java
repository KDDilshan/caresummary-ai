package com.kavindu.caresummaryapi.monitoring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Monitoring endpoints, with the real app and real security.
 * In tests the FHIR server address points nowhere (port 65000), so FHIR health must be DOWN.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActuatorTest {

    @Autowired MockMvc mvc;

    private String login(String user, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"value\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        assertThat(m.find()).isTrue();
        return "Bearer " + m.group(1);
    }

    @Test
    void healthIsOpen_andDownWhenFhirIsUnreachable() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())          // 503 = a monitor would raise an alert
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components").doesNotExist()); // no details without a login
    }

    @Test
    void adminSeesWhichPartIsDown() throws Exception {
        mvc.perform(get("/actuator/health").header("Authorization", login("admin", "admin-test-pw")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.components.fhir.status").value("DOWN"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    @Test
    void metricsNeedAdmin() throws Exception {
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/metrics").header("Authorization", login("doctor", "doctor-test-pw")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/actuator/metrics").header("Authorization", login("admin", "admin-test-pw")))
                .andExpect(status().isOk());
    }

    @Test
    void fhirWorkerPoolIsMonitored() throws Exception {
        mvc.perform(get("/actuator/metrics/executor.pool.core").param("tag", "name:fhir.workers")
                        .header("Authorization", login("admin", "admin-test-pw")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value").value(6.0));
    }
}
