package co.com.bancolombia.api;

import co.com.bancolombia.model.sql.exceptions.InvalidSqlScriptException;
import co.com.bancolombia.model.sql.exceptions.SqlExecutionInfrastructureException;
import co.com.bancolombia.model.sql.exceptions.SqlScriptNotFoundException;
import co.com.bancolombia.usecase.sql.ExecuteSqlScriptUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class SqlExecutionHandler {

    private final ExecuteSqlScriptUseCase useCase;

    public Mono<ServerResponse> executeSql(ServerRequest request) {
        return request.bodyToMono(SqlExecutionRequest.class)
                .flatMap(this::validate)
                .flatMap(body -> useCase.execute(body.fileName()))
                .flatMap(outcome -> ServerResponse.ok().bodyValue(outcome))
                .onErrorResume(this::handleError);
    }

    private Mono<SqlExecutionRequest> validate(SqlExecutionRequest request) {
        String fileName = request.fileName();
        if (fileName == null || fileName.isBlank()) {
            return Mono.error(new InvalidSqlScriptException("fileName must not be empty"));
        }
        if (!fileName.toLowerCase().endsWith(".sql")) {
            return Mono.error(new InvalidSqlScriptException("fileName must have the .sql extension"));
        }
        if (containsPathTraversal(fileName)) {
            return Mono.error(new InvalidSqlScriptException("fileName contains an invalid path"));
        }
        return Mono.just(request);
    }

    private boolean containsPathTraversal(String fileName) {
        return fileName.contains("..")
                || fileName.contains("\\")
                || fileName.startsWith("/")
                || fileName.matches("^[a-zA-Z]:.*");
    }

    private Mono<ServerResponse> handleError(Throwable error) {
        if (error instanceof ServerWebInputException) {
            return badRequest("Invalid request body");
        }
        if (error instanceof InvalidSqlScriptException invalidScript) {
            return badRequest(invalidScript.getMessage());
        }
        if (error instanceof SqlScriptNotFoundException notFound) {
            return ServerResponse.status(HttpStatus.NOT_FOUND)
                    .bodyValue(new ErrorResponse(notFound.getMessage()));
        }
        if (error instanceof SqlExecutionInfrastructureException) {
            log.error("Infrastructure error while executing SQL", error);
            return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .bodyValue(new ErrorResponse("Internal server error"));
        }
        log.error("Unexpected error while executing SQL", error);
        return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyValue(new ErrorResponse("Internal server error"));
    }

    private Mono<ServerResponse> badRequest(String message) {
        return ServerResponse.badRequest().bodyValue(new ErrorResponse(message));
    }
}
