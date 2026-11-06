package com.kavindu.caresummaryapi.patient.dto;

/**
 * Short patient info, used in the patient list.
 * We return our own small record instead of the big FHIR Patient object,
 * so the API stays simple and does not leak fields we don't need.
 */
public record PatientSummary(
        String id,
        String name,
        String gender,
        String birthDate,
        Integer age,
        boolean deceased
) {
}
