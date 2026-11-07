package com.kavindu.caresummaryapi.patient;

import com.kavindu.caresummaryapi.audit.AuditEvent.Action;
import com.kavindu.caresummaryapi.audit.AuditService;
import com.kavindu.caresummaryapi.common.BadRequestException;
import com.kavindu.caresummaryapi.patient.dto.PatientDetail;
import com.kavindu.caresummaryapi.patient.dto.PatientSummary;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for patients (DOCTOR role only, see SecurityConfig).
 *   GET /api/patients            -> list (optional ?name=)
 *   GET /api/patients/{id}       -> one patient with their records
 * Every call is written to the audit log.
 */
@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService patients;
    private final AuditService audit;

    public PatientController(PatientService patients, AuditService audit) {
        this.patients = patients;
        this.audit = audit;
    }

    @GetMapping
    public List<PatientSummary> list(@RequestParam(required = false) String name, Authentication user) {
        if (name != null && name.length() > 50) {
            throw new BadRequestException("Search text is too long (max 50 characters)");
        }
        List<PatientSummary> result = patients.listPatients(name);
        audit.record(user.getName(), Action.LIST_PATIENTS, null,
                (name == null || name.isBlank() ? "all" : "search: " + name) + ", " + result.size() + " results");
        return result;
    }

    @GetMapping("/{id}")
    public PatientDetail get(@PathVariable String id, Authentication user) {
        PatientDetail detail = patients.getPatient(PatientIds.requireValid(id));
        audit.record(user.getName(), Action.VIEW_PATIENT, id, null);
        return detail;
    }
}
