package co.com.bancolombia.usecase.sql;

import co.com.bancolombia.model.management.ExecutionRecord;
import co.com.bancolombia.model.management.gateways.ExecutionHistoryRepository;
import co.com.bancolombia.model.management.gateways.OperationMetrics;
import co.com.bancolombia.model.sql.ExecutionStatus;
import co.com.bancolombia.model.sql.SqlExecutionResult;
import co.com.bancolombia.model.sql.SqlStatement;
import co.com.bancolombia.model.sql.SqlStatementResult;
import co.com.bancolombia.model.sql.gateways.SqlExecutor;
import co.com.bancolombia.model.sql.gateways.SqlScriptStorage;
import co.com.bancolombia.model.sql.gateways.SqlStatementParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

@Log
@RequiredArgsConstructor
public class ExecuteSqlScriptUseCase {

    private static final String NOT_EXECUTED_REASON = "Previous statement failed.";

    private final SqlScriptStorage storage;
    private final SqlStatementParser parser;
    private final SqlExecutor executor;
    private final SqlExecutionReportFormatter formatter;
    private final ExecutionHistoryRepository historyRepository;
    private final OperationMetrics metrics;

    public Mono<SqlExecutionOutcome> execute(String fileName) {
        long start = System.nanoTime();
        log.info("SQL execution requested for source file: " + fileName);

        return storage.get(fileName)
                .flatMap(script -> {
                    List<SqlStatement> statements = parser.parse(script.content());
                    log.info("Source file: " + script.fileName() + ", number of statements: " + statements.size());
                    return executeStatements(script.fileName(), statements, start);
                })
                .doOnError(error -> log.log(Level.SEVERE, "SQL execution failed for source file: " + fileName, error));
    }

    private Mono<SqlExecutionOutcome> executeStatements(String sourceFile, List<SqlStatement> statements, long start) {
        return Flux.fromIterable(statements)
                .concatMap(executor::execute)
                .takeUntil(result -> result.status() == ExecutionStatus.FAILED)
                .collectList()
                .map(results -> completeNotExecuted(statements, results))
                .map(results -> buildResult(sourceFile, results, start))
                .flatMap(this::persistResult)
                .flatMap(outcome -> recordHistory(outcome, start).thenReturn(outcome));
    }

    private List<SqlStatementResult> completeNotExecuted(List<SqlStatement> statements, List<SqlStatementResult> results) {
        List<SqlStatementResult> completed = new ArrayList<>(results);
        for (int i = results.size(); i < statements.size(); i++) {
            SqlStatement statement = statements.get(i);
            completed.add(new SqlStatementResult(
                    statement.sequence(),
                    statement.sql(),
                    ExecutionStatus.NOT_EXECUTED,
                    null,
                    null,
                    NOT_EXECUTED_REASON));
        }
        return completed;
    }

    private SqlExecutionResult buildResult(String sourceFile, List<SqlStatementResult> statements, long start) {
        ExecutionStatus status = statements.stream()
                .anyMatch(statement -> statement.status() == ExecutionStatus.FAILED)
                ? ExecutionStatus.FAILED
                : ExecutionStatus.SUCCESS;
        return new SqlExecutionResult(sourceFile, status, statements, elapsedMs(start));
    }

    private Mono<SqlExecutionOutcome> persistResult(SqlExecutionResult result) {
        String report = formatter.format(result);
        return storage.saveResult(result.sourceFile(), report)
                .map(resultFile -> {
                    log.info("Result file generated: " + resultFile);
                    return SqlExecutionOutcome.from(result, resultFile);
                });
    }

    private Mono<Void> recordHistory(SqlExecutionOutcome outcome, long start) {
        metrics.recordExecution("SQL_EXECUTE", outcome.status().name(), elapsedMs(start));

        ExecutionRecord record = new ExecutionRecord(
                UUID.randomUUID().toString(),
                "SQL_EXECUTE",
                outcome.sourceFile(),
                outcome.status(),
                outcome.totalStatements(),
                outcome.successfulStatements(),
                outcome.failedStatements(),
                outcome.totalExecutionTimeMs(),
                LocalDateTime.now());

        return historyRepository.save(record)
                .doOnError(error -> log.warning("Failed to save execution history: " + error.getMessage()))
                .onErrorResume(error -> Mono.empty());
    }

    private long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}