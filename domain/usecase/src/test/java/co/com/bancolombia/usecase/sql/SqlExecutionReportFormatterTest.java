package co.com.bancolombia.usecase.sql;

import co.com.bancolombia.model.sql.ExecutionStatus;
import co.com.bancolombia.model.sql.SqlExecutionResult;
import co.com.bancolombia.model.sql.SqlStatementResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SqlExecutionReportFormatterTest {

    private final SqlExecutionReportFormatter formatter = new SqlExecutionReportFormatter();

    @Test
    void shouldFormatEveryStatementResult() {
        SqlExecutionResult result = new SqlExecutionResult(
                "approved/update-customers.sql",
                ExecutionStatus.FAILED,
                List.of(
                        new SqlStatementResult(1, "UPDATE customers SET status = 'ACTIVE' WHERE id = 10", ExecutionStatus.SUCCESS, 1L, 15L, null),
                        new SqlStatementResult(2, "UPDATE customers SET status = 'ACTIVE' WHERE id = 20", ExecutionStatus.SUCCESS, 1L, 11L, null),
                        new SqlStatementResult(3, "DELETE FROM customer_sessions WHERE customer_id = 20", ExecutionStatus.FAILED, null, 7L, "relation \"customer_sessions\" does not exist"),
                        new SqlStatementResult(4, "SELECT 1", ExecutionStatus.NOT_EXECUTED, null, null, "Previous statement failed.")),
                33L);

        String report = formatter.format(result);

        assertThat(report)
                .contains("SQL EXECUTION RESULT")
                .contains("Source file:")
                .contains("approved/update-customers.sql")
                .contains("Overall status:")
                .contains("FAILED")
                .contains("Statement #1")
                .contains("Statement #4")
                .contains("SUCCESS")
                .contains("NOT_EXECUTED")
                .contains("Rows affected:")
                .contains("Error:")
                .contains("relation \"customer_sessions\" does not exist")
                .contains("Execution time:")
                .contains("Reason:")
                .contains("Previous statement failed.");
    }
}
