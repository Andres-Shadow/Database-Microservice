package co.com.bancolombia.model.sql;

public record SqlStatementResult(
        int sequence,
        String sql,
        ExecutionStatus status,
        Long rowsAffected,
        Long executionTimeMs,
        String error) {
}
