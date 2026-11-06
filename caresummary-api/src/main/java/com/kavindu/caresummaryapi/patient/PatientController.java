package com.kavindu.caresummaryapi.patient;

import com.kavindu.caresummaryapi.common.BadRequestException;
import com.kavindu.caresummaryapi.patient.dto.PatientDetail;
import com.kavindu.caresummaryapi.patient.dto.PatientSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.regex.Pattern;

/**
 * REST endpoints for patients.
 *   GET /api/patients            -> list (optional ?name=)
 *   GET /api/patients/{id}       -> one patient with their records
 */
@RestController
@RequestMapping("/api/patients")
public class PatientController {

    /** FHIR ids are letters, numbers, '-' and '.', max 64 chars. Anything else is rejected. */
    private static final Pattern FHIR_ID = Pattern.compile("[A-Za-z0-9\\-.]{1,64}");

    private final PatientService patients;

    public PatientController(PatientService patients) {
        this.patients = patients;
    }

    @GetMapping
    public List<PatientSummary> list(@RequestParam(required = false) String name) {
        if (name != null && name.length() > 50) {
            throw new BadRequestException("Search text is too long (max 50 characters)");
        }
        return patients.listPatients(name);
    }

    @GetMapping("/{id}")
    public PatientDetail get(@PathVariable String id) {
        if (!FHIR_ID.matcher(id).matches()) {
            throw new BadRequestException("Invalid patient id");
        }
        return patients.getPatient(id);
    }
}
