package co.com.bancolombia.model.management;

import co.com.bancolombia.model.sql.ExecutionStatus;

import java.time.LocalDateTime;

public record ExecutionRecord(
        String executionId,
        String operationType,
        String sourceFile,
        ExecutionStatus status,
        int totalEntries,
        int successfulEntries,
        int failedEntries,
        long executionTimeMs,
        LocalDateTime executedAt) {
}