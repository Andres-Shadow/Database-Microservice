package co.com.bancolombia.model.user;

import co.com.bancolombia.model.sql.ExecutionStatus;

import java.util.List;

public record BatchOperationResult(
        String sourceFile,
        ExecutionStatus status,
        List<BatchEntryResult> entries,
        long totalExecutionTimeMs) {
}
