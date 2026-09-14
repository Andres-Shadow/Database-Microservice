package co.com.bancolombia.model.sql;

import java.util.List;

public record SqlExecutionResult(
        String sourceFile,
        ExecutionStatus status,
        List<SqlStatementResult> statements,
        long totalExecutionTimeMs) {
}
