package com.kavindu.caresummaryapi.patient;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A small pool of worker threads that run FHIR calls AT THE SAME TIME.
 *
 * Opening one patient needs 3 searches (conditions, medicines, test results).
 * One after another, the wait is A + B + C. In parallel, it is only the slowest one: max(A, B, C).
 *
 * Fixed pool size: at most {@code threads} FHIR calls run together, so a busy moment
 * can't open hundreds of connections to the FHIR server.
 * The pool is also watched by Micrometer (active threads, queue size, task time) -> /actuator/metrics.
 */
@Component
public class FhirWorkers implements DisposableBean {

    static final String METRIC_NAME = "fhir.workers";

    private final ExecutorService pool;
    private final Duration timeout;

    public FhirWorkers(@Value("${app.fhir.threads:6}") int threads,
                       @Value("${app.fhir.parallel-timeout:20s}") Duration timeout,
                       MeterRegistry registry) {
        ExecutorService plain = Executors.newFixedThreadPool(threads, namedThreads());
        // Wraps the pool so every task is timed and the pool size/queue are reported as metrics
        this.pool = ExecutorServiceMetrics.monitor(registry, plain, METRIC_NAME);
        this.timeout = timeout;
    }

    /**
     * Runs all tasks in parallel and waits for all of them (up to the timeout).
     * Results come back in the SAME ORDER as the tasks.
     * If a task throws, the same exception is thrown here, so the normal error handling still works.
     */
    public <T> List<T> runAll(List<Callable<T>> tasks) {
        List<Future<T>> futures;
        try {
            // invokeAll: start every task, then wait. Tasks still running at the timeout are cancelled.
            futures = pool.invokeAll(tasks, timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // keep the "interrupted" flag for the caller
            throw new IllegalStateException("Interrupted while loading patient data", e);
        }

        List<T> results = new ArrayList<>(futures.size());
        for (Future<T> f : futures) {
            try {
                results.add(f.get());
            } catch (CancellationException e) {
                throw new FhirTimeoutException(timeout);
            } catch (ExecutionException e) {
                throw unwrap(e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while loading patient data", e);
            }
        }
        return results;
    }

    /** The task's own exception (e.g. "FHIR server not reachable") is more useful than ExecutionException. */
    private static RuntimeException unwrap(Throwable cause) {
        if (cause instanceof RuntimeException re) {
            return re;
        }
        if (cause instanceof Error err) {
            throw err;
        }
        return new IllegalStateException(cause);
    }

    /** Threads named "fhir-worker-1", "fhir-worker-2"... so they are easy to spot in logs and thread dumps. */
    private static ThreadFactory namedThreads() {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, "fhir-worker-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    /** Spring calls this when the app stops: stop the threads cleanly. */
    @Override
    public void destroy() {
        pool.shutdownNow();
    }
}
