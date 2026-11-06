package com.kavindu.caresummaryapi.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the objects we need to talk to the FHIR server.
 * They are Spring beans, so they are made once and shared by every class that needs them.
 */
@Configuration
public class FhirConfig {

    /** FHIR rules for version R4. Slow to create, so we make only one. */
    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }

    /** HTTP client for the FHIR server. The URL comes from application.properties. */
    @Bean
    public IGenericClient fhirClient(FhirContext ctx, @Value("${fhir.server.url}") String url) {
        ctx.getRestfulClientFactory().setConnectTimeout(5_000);
        ctx.getRestfulClientFactory().setSocketTimeout(30_000);
        return ctx.newRestfulGenericClient(url);
    }
}
