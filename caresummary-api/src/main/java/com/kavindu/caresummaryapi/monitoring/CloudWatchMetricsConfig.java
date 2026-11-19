package com.kavindu.caresummaryapi.monitoring;

import io.micrometer.cloudwatch2.CloudWatchConfig;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClientBuilder;

import java.time.Duration;

/**
 * Sends app metrics to AWS CloudWatch. Only active when CLOUDWATCH_ENABLED=true.
 *
 * How it works: Micrometer keeps the numbers in memory. Every "step" (1 minute) the CloudWatch
 * registry sends them to AWS with the PutMetricData API. In the AWS console you then see graphs
 * (CloudWatch -> Metrics -> CareSummaryAI) and can set alarms, e.g. "email me if loads get slow".
 *
 * Two registries run side by side (Spring Boot joins them into one):
 *  - SimpleMeterRegistry : ALL metrics, for /actuator/metrics on this machine
 *  - CloudWatch          : only "caresummary.*" metrics, because each CloudWatch metric costs money
 *                          after the free 10. JVM/HTTP/thread-pool metrics stay local.
 */
@Configuration
@ConditionalOnProperty(name = "app.metrics.cloudwatch.enabled", havingValue = "true")
public class CloudWatchMetricsConfig {

    /** Metrics whose name starts with this are sent to CloudWatch. */
    static final String CLOUDWATCH_PREFIX = "caresummary.";

    /** The AWS SDK client that talks to CloudWatch. */
    @Bean(destroyMethod = "close")
    public CloudWatchAsyncClient cloudWatchClient(@Value("${app.aws.region}") String region,
                                                  @Value("${app.aws.access-key-id:}") String accessKey,
                                                  @Value("${app.aws.secret-access-key:}") String secretKey) {
        CloudWatchAsyncClientBuilder builder = CloudWatchAsyncClient.builder().region(Region.of(region));
        if (!accessKey.isBlank()) {
            // Keys from .env. Otherwise the SDK finds them itself (~/.aws/credentials, env vars, IAM role)
            builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
        }
        return builder.build();
    }

    @Bean
    public CloudWatchMeterRegistry cloudWatchMeterRegistry(CloudWatchAsyncClient client,
                                                           @Value("${app.metrics.cloudwatch.namespace}") String namespace,
                                                           @Value("${app.metrics.cloudwatch.step}") Duration step) {
        CloudWatchConfig config = new CloudWatchConfig() {
            @Override
            public String get(String key) {
                return null;            // use defaults for everything we don't set below
            }

            @Override
            public String namespace() {
                return namespace;       // the folder name for our metrics in CloudWatch
            }

            @Override
            public Duration step() {
                return step;            // how often metrics are sent
            }
        };
        CloudWatchMeterRegistry registry = new CloudWatchMeterRegistry(config, Clock.SYSTEM, client);
        registry.config().meterFilter(onlyAppMetrics());
        return registry;
    }

    /** Keeps all metrics available locally at /actuator/metrics, next to the filtered CloudWatch one. */
    @Bean
    public SimpleMeterRegistry localMeterRegistry() {
        return new SimpleMeterRegistry();
    }

    /** Lets through only metrics named "caresummary.*"; everything else is not sent to AWS. */
    static MeterFilter onlyAppMetrics() {
        return MeterFilter.denyUnless(id -> id.getName().startsWith(CLOUDWATCH_PREFIX));
    }
}
