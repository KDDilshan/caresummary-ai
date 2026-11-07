package com.kavindu.caresummaryapi.ai;

import com.kavindu.caresummaryapi.patient.dto.ClinicalItem;
import com.kavindu.caresummaryapi.patient.dto.PatientDetail;
import com.kavindu.caresummaryapi.patient.dto.PatientSummary;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The text we send to the AI, built from a patient's records.
 *
 * Privacy: we never send the patient's name, id, address or exact birth date.
 * The AI only sees age, gender and the clinical facts. Each fact starts with its
 * FHIR source, e.g. "[Condition/2266]", so the AI can cite it.
 *
 * @param text          the facts, one per line
 * @param validSources  every source id that appears in the text (used to check the AI's answer)
 */
public record PatientFacts(String text, Set<String> validSources) {

    static final int MAX_CONDITIONS = 60;
    static final int MAX_MEDICATIONS = 60;
    static final int MAX_OBSERVATIONS = 40;

    public static PatientFacts from(PatientDetail detail) {
        StringBuilder sb = new StringBuilder();
        Set<String> sources = new LinkedHashSet<>();

        PatientSummary p = detail.patient();
        sb.append("PATIENT: ")
                .append(p.gender() == null ? "unknown gender" : p.gender())
                .append(", age ").append(p.age() == null ? "unknown" : p.age())
                .append(p.deceased() ? ", deceased" : "")
                .append("\n\n");

        section(sb, sources, "CONDITIONS (status, onset date)", detail.conditions(), MAX_CONDITIONS);
        section(sb, sources, "MEDICATIONS (status, prescribed date)", detail.medications(), MAX_MEDICATIONS);
        section(sb, sources, "RECENT VITAL SIGNS AND LAB RESULTS (value, date)",
                detail.recentObservations(), MAX_OBSERVATIONS);

        return new PatientFacts(sb.toString(), sources);
    }

    private static void section(StringBuilder sb, Set<String> sources, String title,
                                List<ClinicalItem> items, int max) {
        sb.append(title).append(":\n");
        if (items.isEmpty()) {
            sb.append("(none)\n");
        }
        items.stream().limit(max).forEach(i -> {
            sources.add(i.source());
            sb.append('[').append(i.source()).append("] ")
                    .append(i.name())
                    .append(" | ").append(i.detail() == null ? "-" : i.detail())
                    .append(" | ").append(i.date() == null ? "no date" : i.date())
                    .append('\n');
        });
        sb.append('\n');
    }
}
