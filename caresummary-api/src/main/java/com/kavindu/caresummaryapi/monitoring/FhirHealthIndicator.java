package com.kavindu.caresummaryapi.monitoring;

import ca.uhn.fhir.rest.api.SummaryEnum;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;

/**
 * Adds "fhir" to /actuator/health.
 * The app is useless without the FHIR server, so if it can't be reached, health becomes DOWN (HTTP 503)
 * and a monitor (or a load balancer) knows something is wrong.
 */
@Component
public class FhirHealthIndicator extends AbstractHealthIndicator {

    private final IGenericClient fhir;

    public FhirHealthIndicator(IGenericClient fhir) {
        super("FHIR server health check failed");
        this.fhir = fhir;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        // A cheap query: "_summary=count" asks only for the number of patients, not the records
        Bundle result = fhir.search().forResource(Patient.class)
                .summaryMode(SummaryEnum.COUNT)
                .returnBundle(Bundle.class)
                .execute();
        builder.up().withDetail("patients", result.getTotal());
        // If the call throws, AbstractHealthIndicator reports DOWN with the error
    }
}
