package co.com.bancolombia.model.sql.gateways;

import co.com.bancolombia.model.sql.SqlScript;
import reactor.core.publisher.Mono;

public interface SqlScriptStorage {

    Mono<SqlScript> get(String fileName);

    Mono<String> saveResult(String sourceFileName, String content);
}
