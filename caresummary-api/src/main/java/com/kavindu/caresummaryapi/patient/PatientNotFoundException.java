package com.kavindu.caresummaryapi.patient;

public class PatientNotFoundException extends RuntimeException {

    public PatientNotFoundException(String id) {
        super("Patient not found: " + id);
    }
}
