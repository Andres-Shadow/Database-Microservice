package co.com.bancolombia.usecase.sql;

import co.com.bancolombia.model.sql.ExecutionStatus;
import co.com.bancolombia.model.sql.SqlExecutionResult;

public record SqlExecutionOutcome(
        String sourceFile,
        ExecutionStatus status,
        int totalStatements,
        int successfulStatements,
        int failedStatements,
        long totalExecutionTimeMs,
        String resultFile) {

    public static SqlExecutionOutcome from(SqlExecutionResult result, String resultFile) {
        int total = result.statements().size();
        int successful = (int) result.statements().stream()
                .filter(statement -> statement.status() == ExecutionStatus.SUCCESS)
                .count();
        int failed = (int) result.statements().stream()
                .filter(statement -> statement.status() == ExecutionStatus.FAILED)
                .count();
        return new SqlExecutionOutcome(
                result.sourceFile(),
                result.status(),
                total,
                successful,
                failed,
                result.totalExecutionTimeMs(),
                resultFile);
    }
}
