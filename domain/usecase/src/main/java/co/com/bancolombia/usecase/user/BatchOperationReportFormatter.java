package co.com.bancolombia.usecase.user;

import co.com.bancolombia.model.sql.ExecutionStatus;
import co.com.bancolombia.model.user.BatchEntryResult;
import co.com.bancolombia.model.user.BatchOperationResult;

public class BatchOperationReportFormatter {

    public String format(BatchOperationResult result) {
        StringBuilder report = new StringBuilder();
        report.append("BATCH OPERATION RESULT\n");
        report.append("======================\n\n");
        report.append("Source file:\n").append(result.sourceFile()).append("\n\n");
        report.append("Overall status:\n").append(result.status()).append("\n\n");

        for (BatchEntryResult entry : result.entries()) {
            report.append("----------------------------------------\n");
            report.append("Entry #").append(entry.sequence()).append("\n");
            report.append("----------------------------------------\n\n");
            report.append("Operation:\n").append(entry.operation()).append("\n\n");
            report.append("Status:\n").append(entry.status()).append("\n\n");

            if (entry.status() == ExecutionStatus.SUCCESS && entry.rowsAffected() != null) {
                report.append("Rows affected:\n").append(entry.rowsAffected()).append("\n\n");
            }
            if (entry.error() != null) {
                report.append("Error:\n").append(entry.error()).append("\n\n");
            }
            if (entry.executionTimeMs() != null) {
                report.append("Execution time:\n").append(entry.executionTimeMs()).append(" ms\n\n");
            }
        }

        return report.toString();
    }
}
