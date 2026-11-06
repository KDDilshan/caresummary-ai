package com.kavindu.caresummaryapi.patient;

import com.kavindu.caresummaryapi.patient.dto.ClinicalItem;
import com.kavindu.caresummaryapi.patient.dto.PatientSummary;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Type;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.stream.Collectors;

/**
 * Turns big FHIR objects into our small records (DTOs).
 * Pure functions with no server calls, so they are easy to unit test.
 */
public final class FhirMapper {

    private FhirMapper() {
    }

    public static PatientSummary toSummary(Patient p, LocalDate today) {
        String birth = p.hasBirthDate() ? p.getBirthDateElement().getValueAsString() : null;
        boolean deceased = p.hasDeceasedDateTimeType()
                || (p.hasDeceasedBooleanType() && p.getDeceasedBooleanType().booleanValue());

        // Age: up to today, or up to the date of death
        Integer age = null;
        if (birth != null) {
            LocalDate end = p.hasDeceasedDateTimeType()
                    ? LocalDate.parse(dateOnly(p.getDeceasedDateTimeType().getValueAsString()))
                    : today;
            age = Period.between(LocalDate.parse(dateOnly(birth)), end).getYears();
        }

        return new PatientSummary(
                p.getIdElement().getIdPart(),
                displayName(p),
                p.hasGender() ? p.getGender().toCode() : null,
                birth,
                age,
                deceased);
    }

    public static ClinicalItem toItem(Condition c) {
        String status = c.hasClinicalStatus() ? c.getClinicalStatus().getCodingFirstRep().getCode() : null;
        String onset = c.hasOnsetDateTimeType() ? dateOnly(c.getOnsetDateTimeType().getValueAsString()) : null;
        if (c.hasAbatementDateTimeType()) {
            status = status + ", ended " + dateOnly(c.getAbatementDateTimeType().getValueAsString());
        }
        return new ClinicalItem(ref(c.fhirType(), c.getIdElement().getIdPart()), text(c.getCode()), status, onset);
    }

    public static ClinicalItem toItem(MedicationRequest m) {
        String name = m.hasMedicationCodeableConcept() ? text(m.getMedicationCodeableConcept()) : "Unknown medicine";
        String status = m.hasStatus() ? m.getStatus().toCode() : null;
        String date = m.hasAuthoredOn() ? dateOnly(m.getAuthoredOnElement().getValueAsString()) : null;
        return new ClinicalItem(ref(m.fhirType(), m.getIdElement().getIdPart()), name, status, date);
    }

    public static ClinicalItem toItem(Observation o) {
        String value;
        if (o.hasComponent()) {
            // e.g. blood pressure = systolic + diastolic
            value = o.getComponent().stream()
                    .map(comp -> shortName(text(comp.getCode())) + " " + valueText(comp.getValue()))
                    .collect(Collectors.joining(" / "));
        } else {
            value = valueText(o.getValue());
        }
        String date = o.hasEffectiveDateTimeType() ? dateOnly(o.getEffectiveDateTimeType().getValueAsString()) : null;
        return new ClinicalItem(ref(o.fhirType(), o.getIdElement().getIdPart()), text(o.getCode()), value, date);
    }

    // ---------- small helpers ----------

    /** Synthea adds numbers to fake names ("Agnes294"). We remove them so names look normal. */
    static String displayName(Patient p) {
        if (!p.hasName()) {
            return "Unknown";
        }
        HumanName n = p.getNameFirstRep();
        String full = (n.getGivenAsSingleString() + " " + (n.hasFamily() ? n.getFamily() : "")).trim();
        return full.replaceAll("\\d", "").replaceAll("\\s+", " ").trim();
    }

    static String text(CodeableConcept cc) {
        if (cc == null) {
            return null;
        }
        if (cc.hasText()) {
            return cc.getText();
        }
        return cc.hasCoding() ? cc.getCodingFirstRep().getDisplay() : null;
    }

    static String valueText(Type value) {
        if (value instanceof Quantity q && q.hasValue()) {
            String number = q.getValue().setScale(Math.min(q.getValue().scale(), 2), RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString();
            return q.hasUnit() ? number + " " + q.getUnit() : number;
        }
        if (value instanceof CodeableConcept cc) {
            return text(cc);
        }
        return value != null && value.hasPrimitiveValue() ? value.primitiveValue() : null;
    }

    /** "Systolic Blood Pressure" -> "Systolic" (keeps blood pressure lines short). */
    private static String shortName(String name) {
        return name == null ? "" : name.split(" ")[0];
    }

    /** "2019-03-04T10:00:00-05:00" -> "2019-03-04" */
    static String dateOnly(String dateTime) {
        return dateTime == null || dateTime.length() < 10 ? dateTime : dateTime.substring(0, 10);
    }

    private static String ref(String type, String id) {
        return type + "/" + id;
    }
}
