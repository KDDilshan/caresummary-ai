package com.kavindu.caresummaryapi.patient.dto;

import java.util.List;

/** Everything we show (and later send to the AI) for one patient. */
public record PatientDetail(
        PatientSummary patient,
        List<ClinicalItem> conditions,
        List<ClinicalItem> medications,
        List<ClinicalItem> recentObservations
) {
}
