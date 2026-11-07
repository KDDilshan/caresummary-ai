package com.kavindu.caresummaryapi.ai;

import com.kavindu.caresummaryapi.patient.dto.ClinicalItem;
import com.kavindu.caresummaryapi.patient.dto.PatientDetail;
import com.kavindu.caresummaryapi.patient.dto.PatientSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PatientFactsTest {

    private final PatientDetail detail = new PatientDetail(
            new PatientSummary("1413", "Agnes Treutel", "female", "1986-05-02", 39, false),
            List.of(new ClinicalItem("Condition/10", "Hypertension", "active", "2019-01-01")),
            List.of(new ClinicalItem("MedicationRequest/20", "Lisinopril 10 MG", "active", "2019-01-02")),
            List.of());

    @Test
    void neverSendsNameIdOrBirthDateToTheAi() {
        String text = PatientFacts.from(detail).text();

        assertThat(text).doesNotContain("Agnes", "Treutel", "1413", "1986-05-02");
        assertThat(text).contains("female", "age 39");
    }

    @Test
    void everyFactCarriesItsSource_andSourcesAreCollected() {
        PatientFacts facts = PatientFacts.from(detail);

        assertThat(facts.text()).contains("[Condition/10] Hypertension | active | 2019-01-01");
        assertThat(facts.text()).contains("RECENT VITAL SIGNS AND LAB RESULTS (value, date):\n(none)");
        assertThat(facts.validSources()).containsExactly("Condition/10", "MedicationRequest/20");
    }
}
