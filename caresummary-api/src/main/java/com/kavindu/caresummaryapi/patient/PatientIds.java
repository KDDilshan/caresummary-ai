package com.kavindu.caresummaryapi.patient;

import com.kavindu.caresummaryapi.common.BadRequestException;

import java.util.regex.Pattern;

/** FHIR ids are letters, numbers, '-' and '.', max 64 chars. Anything else is rejected before it reaches FHIR. */
public final class PatientIds {

    private static final Pattern FHIR_ID = Pattern.compile("[A-Za-z0-9\\-.]{1,64}");

    private PatientIds() {
    }

    public static String requireValid(String id) {
        if (id == null || !FHIR_ID.matcher(id).matches()) {
            throw new BadRequestException("Invalid patient id");
        }
        return id;
    }
}
