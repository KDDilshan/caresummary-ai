package com.kavindu.caresummaryapi.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Only our own "caresummary.*" metrics may go to CloudWatch (each CloudWatch metric can cost money). */
class CloudWatchFilterTest {

    @Test
    void sendsOnlyAppMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(CloudWatchMetricsConfig.onlyAppMetrics());

        registry.timer("caresummary.patient.load", "outcome", "success").record(java.time.Duration.ofMillis(120));
        registry.counter("jvm.gc.pause").increment();
        registry.counter("http.server.requests").increment();

        assertThat(registry.find("caresummary.patient.load").timer()).isNotNull();
        assertThat(registry.find("jvm.gc.pause").counter()).isNull();
        assertThat(registry.find("http.server.requests").counter()).isNull();
    }
}
