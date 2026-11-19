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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
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

    /** Metric name. Only "caresummary.*" metrics are sent to AWS CloudWatch (see CloudWatchMetricsConfig). */
    static final String LOAD_METRIC = "caresummary.patient.load";

    private final IGenericClient fhir;
    private final FhirWorkers workers;
    private final MeterRegistry metrics;

    // Spring injects: the FHIR client (FhirConfig), the worker pool (FhirWorkers), the metrics registry (Actuator)
    public PatientService(IGenericClient fhir, FhirWorkers workers, MeterRegistry metrics) {
        this.fhir = fhir;
        this.workers = workers;
        this.metrics = metrics;
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

    /**
     * One patient with their illnesses, medicines and latest test results.
     * The time it takes is recorded as the metric "caresummary.patient.load" (outcome = success / error).
     */
    public PatientDetail getPatient(String id) {
        Timer.Sample sample = Timer.start(metrics);   // start the stopwatch
        String outcome = "error";
        try {
            PatientDetail detail = loadPatient(id);
            outcome = "success";
            return detail;
        } finally {
            // "finally" runs even when an exception is thrown, so failed loads are counted too
            sample.stop(metrics.timer(LOAD_METRIC, "outcome", outcome));
        }
    }

    private PatientDetail loadPatient(String id) {
        // 1) Read the patient first. If they don't exist we answer 404 at once, without starting 3 searches.
        Patient patient;
        try {
            patient = fhir.read().resource(Patient.class).withId(id).execute();
        } catch (ResourceNotFoundException | ResourceGoneException e) {
            throw new PatientNotFoundException(id);
        }

        String subject = "Patient/" + id;

        // 2) The 3 searches don't depend on each other, so they run AT THE SAME TIME on the worker threads.
        List<Callable<List<ClinicalItem>>> searches = List.of(
                () -> newestFirst(fetchAll(searchConditions(subject), Condition.class, MAX_ITEMS), FhirMapper::toItem),
                () -> newestFirst(fetchAll(searchMedications(subject), MedicationRequest.class, MAX_ITEMS), FhirMapper::toItem),
                () -> newestFirst(resourcesIn(searchObservations(subject), Observation.class), FhirMapper::toItem));

        List<List<ClinicalItem>> results = workers.runAll(searches);   // same order as the list above

        return new PatientDetail(
                FhirMapper.toSummary(patient, LocalDate.now()),
                results.get(0),    // conditions
                results.get(1),    // medicines
                results.get(2));   // observations
    }

    private Bundle searchConditions(String subject) {
        return fhir.search().forResource(Condition.class)
                .where(Condition.SUBJECT.hasId(subject))
                .count(200).returnBundle(Bundle.class).execute();
    }

    private Bundle searchMedications(String subject) {
        return fhir.search().forResource(MedicationRequest.class)
                .where(MedicationRequest.SUBJECT.hasId(subject))
                .count(200).returnBundle(Bundle.class).execute();
    }

    /** Only the newest vital signs and lab results (not surveys): a patient can have thousands. */
    private Bundle searchObservations(String subject) {
        return fhir.search().forResource(Observation.class)
                .where(Observation.SUBJECT.hasId(subject))
                .and(Observation.CATEGORY.exactly().codes("vital-signs", "laboratory"))
                .sort().descending(Observation.DATE)
                .count(RECENT_OBSERVATIONS).returnBundle(Bundle.class).execute();
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
