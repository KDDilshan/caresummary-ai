package com.kavindu.caresummaryapi.patient;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain unit tests for the worker pool (no Spring, no FHIR server).
 * "Slow FHIR calls" are faked with Thread.sleep.
 */
class FhirWorkersTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private FhirWorkers workers;

    @AfterEach
    void stopPool() {
        if (workers != null) {
            workers.destroy();
        }
    }

    /** A fake FHIR call: waits, then returns a value. */
    private static Callable<String> slowTask(String result, long millis) {
        return () -> {
            Thread.sleep(millis);
            return result;
        };
    }

    @Test
    void runsTasksAtTheSameTime() {
        workers = new FhirWorkers(3, Duration.ofSeconds(5), registry);

        long start = System.nanoTime();
        workers.runAll(List.of(slowTask("a", 400), slowTask("b", 400), slowTask("c", 400)));
        long tookMs = (System.nanoTime() - start) / 1_000_000;

        // One after another would take 1200 ms or more. In parallel it is about 400 ms.
        assertThat(tookMs).isLessThan(1000);
    }

    @Test
    void usesDifferentWorkerThreads() {
        workers = new FhirWorkers(3, Duration.ofSeconds(5), registry);
        Set<String> threadNames = ConcurrentHashMap.newKeySet();
        Callable<String> rememberThread = () -> {
            threadNames.add(Thread.currentThread().getName());
            Thread.sleep(200);   // keep the thread busy so the next task needs another one
            return "ok";
        };

        workers.runAll(List.of(rememberThread, rememberThread, rememberThread));

        assertThat(threadNames).hasSize(3).allMatch(name -> name.startsWith("fhir-worker-"));
    }

    @Test
    void keepsResultsInTaskOrder() {
        workers = new FhirWorkers(3, Duration.ofSeconds(5), registry);

        // The first task finishes LAST, but its result must still come first
        List<String> results = workers.runAll(List.of(slowTask("first", 300), slowTask("second", 10), slowTask("third", 100)));

        assertThat(results).containsExactly("first", "second", "third");
    }

    @Test
    void throwsTheTasksOwnException() {
        workers = new FhirWorkers(3, Duration.ofSeconds(5), registry);
        Callable<String> failing = () -> {
            throw new PatientNotFoundException("42");
        };

        // Not wrapped in ExecutionException, so ApiExceptionHandler still turns it into the right status code
        assertThatThrownBy(() -> workers.runAll(List.of(slowTask("ok", 10), failing)))
                .isInstanceOf(PatientNotFoundException.class);
    }

    @Test
    void givesUpAfterTheTimeout() {
        workers = new FhirWorkers(2, Duration.ofMillis(200), registry);

        long start = System.nanoTime();
        assertThatThrownBy(() -> workers.runAll(List.of(slowTask("fast", 10), slowTask("stuck", 5_000))))
                .isInstanceOf(FhirTimeoutException.class);
        long tookMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(tookMs).isLessThan(2000);   // did not wait the full 5 seconds
    }

    @Test
    void poolIsMonitoredWithMetrics() {
        workers = new FhirWorkers(3, Duration.ofSeconds(5), registry);

        workers.runAll(List.of(slowTask("a", 10), slowTask("b", 10), slowTask("c", 10)));

        // Micrometer timed each task. Same numbers show up at /actuator/metrics/executor
        long timedTasks = registry.get("executor").tag("name", FhirWorkers.METRIC_NAME).timer().count();
        assertThat(timedTasks).isEqualTo(3);
        double poolSize = registry.get("executor.pool.core").tag("name", FhirWorkers.METRIC_NAME).gauge().value();
        assertThat(poolSize).isEqualTo(3);
    }
}
