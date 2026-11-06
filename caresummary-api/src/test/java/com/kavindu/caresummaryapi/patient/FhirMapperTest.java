package com.kavindu.caresummaryapi.patient;

import com.kavindu.caresummaryapi.patient.dto.ClinicalItem;
import com.kavindu.caresummaryapi.patient.dto.PatientSummary;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for FhirMapper: no server needed, we build FHIR objects by hand. */
class FhirMapperTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);

    @Test
    void patientSummary_removesSyntheaNumbersAndCalculatesAge() {
        Patient p = new Patient();
        p.setId("123");
        p.addName().addGiven("Agnes294").setFamily("Treutel973");
        p.setGender(Enumerations.AdministrativeGender.FEMALE);
        p.setBirthDateElement(new DateType("1980-11-07"));

        PatientSummary s = FhirMapper.toSummary(p, TODAY);

        assertThat(s.id()).isEqualTo("123");
        assertThat(s.name()).isEqualTo("Agnes Treutel");
        assertThat(s.gender()).isEqualTo("female");
        assertThat(s.age()).isEqualTo(45);   // birthday is tomorrow, so still 45
        assertThat(s.deceased()).isFalse();
    }

    @Test
    void patientSummary_deceasedPatientAgeStopsAtDeath() {
        Patient p = new Patient();
        p.setId("9");
        p.setBirthDateElement(new DateType("1950-01-01"));
        p.setDeceased(new DateTimeType("2000-06-15T10:00:00-05:00"));

        PatientSummary s = FhirMapper.toSummary(p, TODAY);

        assertThat(s.deceased()).isTrue();
        assertThat(s.age()).isEqualTo(50);
        assertThat(s.name()).isEqualTo("Unknown");
    }

    @Test
    void condition_keepsSourceReferenceStatusAndOnset() {
        Condition c = new Condition();
        c.setId("55");
        c.setCode(new CodeableConcept().setText("Diabetes mellitus type 2"));
        c.setClinicalStatus(new CodeableConcept().addCoding(new Coding().setCode("active")));
        c.setOnset(new DateTimeType("2019-03-04T10:00:00-05:00"));

        ClinicalItem item = FhirMapper.toItem(c);

        assertThat(item.source()).isEqualTo("Condition/55");
        assertThat(item.name()).isEqualTo("Diabetes mellitus type 2");
        assertThat(item.detail()).isEqualTo("active");
        assertThat(item.date()).isEqualTo("2019-03-04");
    }

    @Test
    void medication_usesCodingDisplayWhenNoText() {
        MedicationRequest m = new MedicationRequest();
        m.setId("77");
        m.setMedication(new CodeableConcept().addCoding(new Coding().setDisplay("Metformin 500 MG Oral Tablet")));
        m.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);

        ClinicalItem item = FhirMapper.toItem(m);

        assertThat(item.source()).isEqualTo("MedicationRequest/77");
        assertThat(item.name()).isEqualTo("Metformin 500 MG Oral Tablet");
        assertThat(item.detail()).isEqualTo("active");
        assertThat(item.date()).isNull();
    }

    @Test
    void observation_formatsValueAndBloodPressureComponents() {
        Observation sugar = new Observation();
        sugar.setId("1");
        sugar.setCode(new CodeableConcept().setText("Hemoglobin A1c"));
        sugar.setValue(new Quantity().setValue(new BigDecimal("6.8000")).setUnit("%"));
        assertThat(FhirMapper.toItem(sugar).detail()).isEqualTo("6.8 %");

        Observation bp = new Observation();
        bp.setId("2");
        bp.setCode(new CodeableConcept().setText("Blood pressure panel"));
        bp.addComponent().setCode(new CodeableConcept().setText("Systolic Blood Pressure"))
                .setValue(new Quantity().setValue(120).setUnit("mm[Hg]"));
        bp.addComponent().setCode(new CodeableConcept().setText("Diastolic Blood Pressure"))
                .setValue(new Quantity().setValue(80).setUnit("mm[Hg]"));
        assertThat(FhirMapper.toItem(bp).detail()).isEqualTo("Systolic 120 mm[Hg] / Diastolic 80 mm[Hg]");
    }
}
