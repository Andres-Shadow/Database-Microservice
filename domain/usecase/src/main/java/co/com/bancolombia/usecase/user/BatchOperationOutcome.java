package co.com.bancolombia.usecase.user;

import co.com.bancolombia.model.sql.ExecutionStatus;
import co.com.bancolombia.model.user.BatchOperationResult;

public record BatchOperationOutcome(
        String sourceFile,
        ExecutionStatus status,
        int totalEntries,
        int successfulEntries,
        int failedEntries,
        long totalExecutionTimeMs,
        String resultFile) {

    public static BatchOperationOutcome from(BatchOperationResult result, String resultFile) {
        int total = result.entries().size();
        int successful = (int) result.entries().stream()
                .filter(entry -> entry.status() == ExecutionStatus.SUCCESS)
                .count();
        int failed = (int) result.entries().stream()
                .filter(entry -> entry.status() == ExecutionStatus.FAILED)
                .count();
        return new BatchOperationOutcome(
                result.sourceFile(),
                result.status(),
                total,
                successful,
                failed,
                result.totalExecutionTimeMs(),
                resultFile);
    }
}
