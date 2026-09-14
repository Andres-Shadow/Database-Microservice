package co.com.bancolombia.r2dbc;

import co.com.bancolombia.model.sql.ExecutionStatus;
import co.com.bancolombia.model.sql.SqlStatement;
import co.com.bancolombia.model.sql.SqlStatementResult;
import co.com.bancolombia.model.sql.exceptions.SqlExecutionInfrastructureException;
import co.com.bancolombia.model.sql.gateways.SqlExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostgresSqlExecutor implements SqlExecutor {

    private final DatabaseClient databaseClient;

    @Override
    public Mono<SqlStatementResult> execute(SqlStatement statement) {
        long start = System.nanoTime();

        return databaseClient.sql(statement.sql())
                .fetch()
                .rowsUpdated()
                .map(rows -> toResult(statement, ExecutionStatus.SUCCESS, rows, null, start))
                .onErrorResume(error -> handleError(statement, start, error));
    }

    private Mono<SqlStatementResult> handleError(SqlStatement statement, long start, Throwable error) {
        if (isInfrastructureError(error)) {
            return Mono.error(new SqlExecutionInfrastructureException(
                    "Unable to execute statement #" + statement.sequence(), error));
        }

        SqlStatementResult result = toResult(statement, ExecutionStatus.FAILED, null, rootMessage(error), start);
        log.warn("Statement #{} failed, time: {} ms", result.sequence(), result.executionTimeMs());
        return Mono.just(result);
    }

    private SqlStatementResult toResult(SqlStatement statement, ExecutionStatus status, Long rowsAffected,
                                        String error, long start) {
        SqlStatementResult result = new SqlStatementResult(
                statement.sequence(),
                statement.sql(),
                status,
                rowsAffected,
                elapsedMs(start),
                error);
        if (status == ExecutionStatus.SUCCESS) {
            log.info("Statement #{} executed successfully, rows affected: {}, time: {} ms",
                    result.sequence(), result.rowsAffected(), result.executionTimeMs());
        }
        return result;
    }

    private boolean isInfrastructureError(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof DataAccessResourceFailureException
                    || current instanceof java.net.ConnectException
                    || current instanceof java.net.SocketException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : error.getClass().getSimpleName();
    }

    private long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
