package co.com.bancolombia.model.sql.gateways;

import co.com.bancolombia.model.sql.SqlStatement;
import co.com.bancolombia.model.sql.SqlStatementResult;
import reactor.core.publisher.Mono;

public interface SqlExecutor {

    Mono<SqlStatementResult> execute(SqlStatement statement);
}
