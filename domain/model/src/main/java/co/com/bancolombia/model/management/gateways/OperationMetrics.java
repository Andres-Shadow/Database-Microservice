package co.com.bancolombia.model.management.gateways;

public interface OperationMetrics {

    void recordExecution(String operationType, String status, long durationMs);

    void recordBackupOperation(String operation);
}