package co.com.bancolombia.usecase.sql;

import co.com.bancolombia.model.sql.ExecutionStatus;
import co.com.bancolombia.model.sql.SqlExecutionResult;
import co.com.bancolombia.model.sql.SqlScript;
import co.com.bancolombia.model.sql.SqlStatement;
import co.com.bancolombia.model.sql.SqlStatementResult;
import co.com.bancolombia.model.sql.exceptions.SqlExecutionInfrastructureException;
import co.com.bancolombia.model.sql.gateways.SqlExecutor;
import co.com.bancolombia.model.sql.gateways.SqlScriptStorage;
import co.com.bancolombia.model.sql.gateways.SqlStatementParser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecuteSqlScriptUseCaseTest {

    private final SqlScriptStorage storage = mock(SqlScriptStorage.class);
    private final SqlStatementParser parser = mock(SqlStatementParser.class);
    private final SqlExecutor executor = mock(SqlExecutor.class);
    private final SqlExecutionReportFormatter formatter = mock(SqlExecutionReportFormatter.class);

    private final ExecuteSqlScriptUseCase useCase =
            new ExecuteSqlScriptUseCase(storage, parser, executor, formatter);

    @Test
    void shouldExecuteAllStatementsSuccessfullyAndPersistResult() {
        List<SqlStatement> statements = List.of(
                new SqlStatement(1, "UPDATE 1"),
                new SqlStatement(2, "UPDATE 2"));
        when(storage.get("approved/test.sql")).thenReturn(Mono.just(new SqlScript("approved/test.sql", "content")));
        when(parser.parse("content")).thenReturn(statements);
        when(executor.execute(statements.get(0)))
                .thenReturn(Mono.just(new SqlStatementResult(1, "UPDATE 1", ExecutionStatus.SUCCESS, 1L, 10L, null)));
        when(executor.execute(statements.get(1)))
                .thenReturn(Mono.just(new SqlStatementResult(2, "UPDATE 2", ExecutionStatus.SUCCESS, 1L, 12L, null)));
        when(formatter.format(any(SqlExecutionResult.class))).thenReturn("report");
        when(storage.saveResult("approved/test.sql", "report")).thenReturn(Mono.just("results/test-result.txt"));

        StepVerifier.create(useCase.execute("approved/test.sql"))
                .assertNext(outcome -> {
                    assertThat(outcome.status()).isEqualTo(ExecutionStatus.SUCCESS);
                    assertThat(outcome.totalStatements()).isEqualTo(2);
                    assertThat(outcome.successfulStatements()).isEqualTo(2);
                    assertThat(outcome.failedStatements()).isZero();
                    assertThat(outcome.resultFile()).isEqualTo("results/test-result.txt");
                })
                .verifyComplete();

        verify(storage).saveResult(eq("approved/test.sql"), eq("report"));
    }

    @Test
    void shouldStopOnFirstFailureAndMarkRemainingAsNotExecuted() {
        List<SqlStatement> statements = List.of(
                new SqlStatement(1, "UPDATE 1"),
                new SqlStatement(2, "UPDATE 2"),
                new SqlStatement(3, "DELETE"),
                new SqlStatement(4, "SELECT"));
        when(storage.get("approved/test.sql")).thenReturn(Mono.just(new SqlScript("approved/test.sql", "content")));
        when(parser.parse("content")).thenReturn(statements);
        when(executor.execute(statements.get(0)))
                .thenReturn(Mono.just(new SqlStatementResult(1, "UPDATE 1", ExecutionStatus.SUCCESS, 1L, 10L, null)));
        when(executor.execute(statements.get(1)))
                .thenReturn(Mono.just(new SqlStatementResult(2, "UPDATE 2", ExecutionStatus.SUCCESS, 1L, 12L, null)));
        when(executor.execute(statements.get(2)))
                .thenReturn(Mono.just(new SqlStatementResult(3, "DELETE", ExecutionStatus.FAILED, null, 8L, "error")));
        when(formatter.format(any(SqlExecutionResult.class))).thenReturn("report");
        when(storage.saveResult("approved/test.sql", "report")).thenReturn(Mono.just("results/test-result.txt"));

        StepVerifier.create(useCase.execute("approved/test.sql"))
                .assertNext(outcome -> {
                    assertThat(outcome.status()).isEqualTo(ExecutionStatus.FAILED);
                    assertThat(outcome.totalStatements()).isEqualTo(4);
                    assertThat(outcome.failedStatements()).isEqualTo(1);
                    assertThat(outcome.successfulStatements()).isEqualTo(2);
                })
                .verifyComplete();

        ArgumentCaptor<SqlExecutionResult> captor = ArgumentCaptor.forClass(SqlExecutionResult.class);
        verify(formatter).format(captor.capture());
        assertThat(captor.getValue().statements()).hasSize(4);
        assertThat(captor.getValue().statements().get(3).status()).isEqualTo(ExecutionStatus.NOT_EXECUTED);
        verify(executor, never()).execute(statements.get(3));
    }

    @Test
    void shouldPropagateInfrastructureError() {
        List<SqlStatement> statements = List.of(new SqlStatement(1, "UPDATE 1"));
        when(storage.get("approved/test.sql")).thenReturn(Mono.just(new SqlScript("approved/test.sql", "content")));
        when(parser.parse("content")).thenReturn(statements);
        when(executor.execute(any()))
                .thenReturn(Mono.error(new SqlExecutionInfrastructureException("db down", new RuntimeException("boom"))));

        StepVerifier.create(useCase.execute("approved/test.sql"))
                .expectError(SqlExecutionInfrastructureException.class)
                .verify();
    }
}
