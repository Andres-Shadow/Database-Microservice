package co.com.bancolombia.metrics.aws;

import co.com.bancolombia.model.management.gateways.OperationMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class MicrometerOperationMetrics implements OperationMetrics {

    private final MeterRegistry registry;

    public MicrometerOperationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordExecution(String operationType, String status, long durationMs) {
        registry.counter("ms.db.operations.total",
                "operation", operationType,
                "status", status).increment();
        registry.timer("ms.db.operations.duration",
                "operation", operationType)
                .record(Duration.ofMillis(durationMs));
    }

    @Override
    public void recordBackupOperation(String operation) {
        registry.counter("ms.db.backups.total",
                "operation", operation).increment();
    }
}