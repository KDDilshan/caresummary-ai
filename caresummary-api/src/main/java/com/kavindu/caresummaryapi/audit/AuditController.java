package com.kavindu.caresummaryapi.audit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** GET /api/audit : latest 200 events (ADMIN only, see SecurityConfig). Optional ?patientId= */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    public List<AuditEvent> latest(@RequestParam(required = false) String patientId) {
        return audit.latest(patientId);
    }
}
