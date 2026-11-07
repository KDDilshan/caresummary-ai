package com.kavindu.caresummaryapi.ai;

import com.kavindu.caresummaryapi.ai.dto.AiResponse;
import com.kavindu.caresummaryapi.ai.dto.QuestionRequest;
import com.kavindu.caresummaryapi.audit.AuditEvent.Action;
import com.kavindu.caresummaryapi.audit.AuditService;
import com.kavindu.caresummaryapi.patient.PatientIds;
import com.kavindu.caresummaryapi.patient.PatientService;
import com.kavindu.caresummaryapi.patient.dto.PatientDetail;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI endpoints (DOCTOR role only).
 *   POST /api/patients/{id}/summary?refresh=false  -> AI summary with source links
 *   POST /api/patients/{id}/ask  {"question": ...} -> AI answer with source links
 *   GET  /api/ai/status                            -> is the AI configured?
 */
@RestController
@RequestMapping("/api")
public class AiController {

    private final PatientService patients;
    private final AiService ai;
    private final AuditService audit;

    public AiController(PatientService patients, AiService ai, AuditService audit) {
        this.patients = patients;
        this.ai = ai;
        this.audit = audit;
    }

    @GetMapping("/ai/status")
    public Map<String, Boolean> status() {
        return Map.of("enabled", ai.isEnabled());
    }

    @PostMapping("/patients/{id}/summary")
    public AiResponse summary(@PathVariable String id,
                              @RequestParam(defaultValue = "false") boolean refresh,
                              Authentication user) {
        PatientDetail detail = patients.getPatient(PatientIds.requireValid(id));
        AiResponse r = ai.summarize(detail, refresh);
        audit.record(user.getName(), Action.AI_SUMMARY, id, lineInfo(r));
        return r;
    }

    @PostMapping("/patients/{id}/ask")
    public AiResponse ask(@PathVariable String id,
                          @Valid @RequestBody QuestionRequest body,
                          Authentication user) {
        PatientDetail detail = patients.getPatient(PatientIds.requireValid(id));
        AiResponse r = ai.answer(detail, body.question().trim());
        audit.record(user.getName(), Action.AI_QUESTION, id, "Q: " + body.question().trim() + " | " + lineInfo(r));
        return r;
    }

    private static String lineInfo(AiResponse r) {
        return r.lines().size() + " lines, " + r.unverified() + " unverified";
    }
}
