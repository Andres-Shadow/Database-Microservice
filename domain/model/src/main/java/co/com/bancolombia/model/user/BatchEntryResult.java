package co.com.bancolombia.model.user;

import co.com.bancolombia.model.sql.ExecutionStatus;

public record BatchEntryResult(
        int sequence,
        String operation,
        ExecutionStatus status,
        Long rowsAffected,
        Long executionTimeMs,
        String error) {
}
