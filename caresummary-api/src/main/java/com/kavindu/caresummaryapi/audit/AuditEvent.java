package com.kavindu.caresummaryapi.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row in the audit log: who did what, to which patient, and when.
 * Health systems must be able to show exactly who opened a patient's record.
 * Rows are only ever added, never changed or deleted by the app.
 */
@Entity
@Table(name = "audit_event", indexes = @Index(name = "idx_audit_at", columnList = "at"))
public class AuditEvent {

    public enum Action { LOGIN_SUCCESS, LOGIN_FAILED, LIST_PATIENTS, VIEW_PATIENT, AI_SUMMARY, AI_QUESTION }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant at;

    @Column(nullable = false, length = 100)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Action action;

    @Column(length = 64)
    private String patientId;

    @Column(length = 400)
    private String detail;

    protected AuditEvent() {   // needed by JPA
    }

    public AuditEvent(String username, Action action, String patientId, String detail) {
        this.at = Instant.now();
        this.username = username;
        this.action = action;
        this.patientId = patientId;
        this.detail = detail == null || detail.length() <= 400 ? detail : detail.substring(0, 400);
    }

    public Long getId() { return id; }
    public Instant getAt() { return at; }
    public String getUsername() { return username; }
    public Action getAction() { return action; }
    public String getPatientId() { return patientId; }
    public String getDetail() { return detail; }
}
