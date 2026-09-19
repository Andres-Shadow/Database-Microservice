package co.com.bancolombia.api;

import co.com.bancolombia.model.sql.exceptions.SqlExecutionInfrastructureException;
import co.com.bancolombia.model.user.exceptions.BatchFileNotFoundException;
import co.com.bancolombia.model.user.exceptions.InvalidBatchFileException;
import co.com.bancolombia.usecase.user.ExecuteDeleteUserUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeleteUserHandler {

    private final ExecuteDeleteUserUseCase useCase;

    public Mono<ServerResponse> execute(ServerRequest request) {
        return useCase.execute()
                .flatMap(outcome -> ServerResponse.ok().bodyValue(outcome))
                .onErrorResume(this::handleError);
    }

    private Mono<ServerResponse> handleError(Throwable error) {
        if (error instanceof InvalidBatchFileException invalidFile) {
            return ServerResponse.badRequest().bodyValue(new ErrorResponse(invalidFile.getMessage()));
        }
        if (error instanceof BatchFileNotFoundException notFound) {
            return ServerResponse.status(HttpStatus.NOT_FOUND)
                    .bodyValue(new ErrorResponse(notFound.getMessage()));
        }
        if (error instanceof SqlExecutionInfrastructureException) {
            log.error("Infrastructure error during delete user operation", error);
            return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .bodyValue(new ErrorResponse("Internal server error"));
        }
        log.error("Unexpected error during delete user operation", error);
        return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyValue(new ErrorResponse("Internal server error"));
    }
}
