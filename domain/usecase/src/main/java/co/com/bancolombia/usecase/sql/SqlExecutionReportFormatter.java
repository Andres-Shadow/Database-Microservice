package co.com.bancolombia.usecase.sql;

import co.com.bancolombia.model.sql.ExecutionStatus;
import co.com.bancolombia.model.sql.SqlExecutionResult;
import co.com.bancolombia.model.sql.SqlStatementResult;

public class SqlExecutionReportFormatter {

    public String format(SqlExecutionResult result) {
        StringBuilder report = new StringBuilder();
        report.append("SQL EXECUTION RESULT\n");
        report.append("====================\n\n");
        report.append("Source file:\n").append(result.sourceFile()).append("\n\n");
        report.append("Overall status:\n").append(result.status()).append("\n\n");

        for (SqlStatementResult statement : result.statements()) {
            report.append("----------------------------------------\n");
            report.append("Statement #").append(statement.sequence()).append("\n");
            report.append("----------------------------------------\n\n");
            report.append("SQL:\n").append(statement.sql()).append("\n\n");

            if (statement.status() == ExecutionStatus.NOT_EXECUTED) {
                report.append("Status:\n").append(statement.status()).append("\n\n");
                report.append("Reason:\n").append(statement.error()).append("\n\n");
                continue;
            }

            report.append("Status:\n").append(statement.status()).append("\n\n");
            if (statement.rowsAffected() != null) {
                report.append("Rows affected:\n").append(statement.rowsAffected()).append("\n\n");
            }
            if (statement.error() != null) {
                report.append("Error:\n").append(statement.error()).append("\n\n");
            }
            if (statement.executionTimeMs() != null) {
                report.append("Execution time:\n").append(statement.executionTimeMs()).append(" ms\n\n");
            }
        }

        return report.toString();
    }
}
