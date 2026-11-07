package com.kavindu.caresummaryapi.audit;

import com.kavindu.caresummaryapi.audit.AuditEvent.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditRepository repo;

    public AuditService(AuditRepository repo) {
        this.repo = repo;
    }

    public void record(String username, Action action, String patientId, String detail) {
        try {
            repo.save(new AuditEvent(username, action, patientId, detail));
        } catch (RuntimeException e) {
            // Never lose the event silently: at least write it to the log file
            log.error("AUDIT WRITE FAILED user={} action={} patient={} detail={}", username, action, patientId, detail, e);
        }
    }

    public List<AuditEvent> latest(String patientId) {
        return patientId == null || patientId.isBlank()
                ? repo.findTop200ByOrderByAtDesc()
                : repo.findTop200ByPatientIdOrderByAtDesc(patientId);
    }
}
