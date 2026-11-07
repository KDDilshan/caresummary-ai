package com.kavindu.caresummaryapi.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data writes the SQL for us from the method names. */
public interface AuditRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findTop200ByOrderByAtDesc();

    List<AuditEvent> findTop200ByPatientIdOrderByAtDesc(String patientId);
}
