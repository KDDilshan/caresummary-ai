package com.kavindu.caresummaryapi.patient.dto;

/**
 * One line of a patient's record: an illness, a medicine or a test result.
 *
 * @param source FHIR reference, e.g. "Condition/123". The AI summary will point back to this,
 *               so a doctor can check where each statement came from.
 * @param name   what it is, e.g. "Diabetes mellitus type 2"
 * @param detail extra info: status for illnesses/medicines, value + unit for test results
 * @param date   when it started / was prescribed / was measured (yyyy-MM-dd)
 */
public record ClinicalItem(
        String source,
        String name,
        String detail,
        String date
) {
}
