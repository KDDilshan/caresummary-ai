package com.kavindu.caresummaryapi.patient;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.server.exceptions.ResourceGoneException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import com.kavindu.caresummaryapi.patient.dto.ClinicalItem;
import com.kavindu.caresummaryapi.patient.dto.PatientDetail;
import com.kavindu.caresummaryapi.patient.dto.PatientSummary;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Reads patient data from the FHIR server and returns our small DTOs.
 * The controller never talks to FHIR directly; it only calls this class.
 */
@Service
public class PatientService {

    /** Safety limits, so one huge patient record can't use all the memory. */
    static final int MAX_PATIENTS = 200;
    static final int MAX_ITEMS = 500;
    static final int RECENT_OBSERVATIONS = 40;

    private final IGenericClient fhir;

    public PatientService(IGenericClient fhir) {   // Spring injects the bean from FhirConfig
        this.fhir = fhir;
    }

    /** GET Patient?name=... : all patients, or those whose name starts with the search text. */
    public List<PatientSummary> listPatients(String nameSearch) {
        IQuery<IBaseBundle> query = fhir.search().forResource(Patient.class);
        if (nameSearch != null && !nameSearch.isBlank()) {
            query = query.where(Patient.NAME.matches().value(nameSearch.trim()));
        }
        Bundle first = query.count(100).returnBundle(Bundle.class).execute();

        LocalDate today = LocalDate.now();
        return fetchAll(first, Patient.class, MAX_PATIENTS).stream()
                .map(p -> FhirMapper.toSummary(p, today))
                .sorted(Comparator.comparing(PatientSummary::name))
                .toList();
    }

    /** One patient with their illnesses, medicines and latest test results. */
    public PatientDetail getPatient(String id) {
        Patient patient;
        try {
            patient = fhir.read().resource(Patient.class).withId(id).execute();
        } catch (ResourceNotFoundException | ResourceGoneException e) {
            throw new PatientNotFoundException(id);
        }

        String subject = "Patient/" + id;

        Bundle conditionBundle = fhir.search().forResource(Condition.class)
                .where(Condition.SUBJECT.hasId(subject))
                .count(200).returnBundle(Bundle.class).execute();

        Bundle medicationBundle = fhir.search().forResource(MedicationRequest.class)
                .where(MedicationRequest.SUBJECT.hasId(subject))
                .count(200).returnBundle(Bundle.class).execute();

        // Only the newest vital signs and lab results (not surveys): a patient can have thousands
        Bundle observationBundle = fhir.search().forResource(Observation.class)
                .where(Observation.SUBJECT.hasId(subject))
                .and(Observation.CATEGORY.exactly().codes("vital-signs", "laboratory"))
                .sort().descending(Observation.DATE)
                .count(RECENT_OBSERVATIONS).returnBundle(Bundle.class).execute();

        return new PatientDetail(
                FhirMapper.toSummary(patient, LocalDate.now()),
                newestFirst(fetchAll(conditionBundle, Condition.class, MAX_ITEMS), FhirMapper::toItem),
                newestFirst(fetchAll(medicationBundle, MedicationRequest.class, MAX_ITEMS), FhirMapper::toItem),
                newestFirst(resourcesIn(observationBundle, Observation.class), FhirMapper::toItem));
    }

    // ---------- helpers ----------

    /**
     * FHIR returns results in pages (a Bundle with a "next" link).
     * This follows the "next" links until there are no more pages, or we reach the limit.
     */
    private <T extends Resource> List<T> fetchAll(Bundle page, Class<T> type, int limit) {
        List<T> all = new ArrayList<>();
        while (page != null) {
            for (T r : resourcesIn(page, type)) {
                all.add(r);
                if (all.size() >= limit) {
                    return all;
                }
            }
            page = page.getLink(Bundle.LINK_NEXT) != null
                    ? fhir.loadPage().next(page).execute()
                    : null;
        }
        return all;
    }

    private static <T extends Resource> List<T> resourcesIn(Bundle bundle, Class<T> type) {
        return bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    private static <T> List<ClinicalItem> newestFirst(List<T> resources, Function<T, ClinicalItem> mapper) {
        return resources.stream()
                .map(mapper)
                .sorted(Comparator.comparing(ClinicalItem::date, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }
}
