package co.com.bancolombia.api;

import co.com.bancolombia.model.sql.exceptions.SqlExecutionInfrastructureException;
import co.com.bancolombia.model.user.exceptions.BackupNotFoundException;
import co.com.bancolombia.model.user.exceptions.InvalidBatchFileException;
import co.com.bancolombia.usecase.user.RestoreUserFromBackupUseCase;
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
public class RestoreUserHandler {

    private final RestoreUserFromBackupUseCase useCase;

    public Mono<ServerResponse> execute(ServerRequest request) {
        return request.bodyToMono(RestoreUserRequest.class)
                .flatMap(this::validate)
                .flatMap(body -> useCase.execute(body.backupId()))
                .flatMap(message -> ServerResponse.ok().bodyValue(new SuccessResponse(message)))
                .onErrorResume(this::handleError);
    }

    private Mono<RestoreUserRequest> validate(RestoreUserRequest req) {
        if (req.backupId() == null || req.backupId().isBlank()) {
            return Mono.error(new InvalidBatchFileException("backupId must not be empty"));
        }
        return Mono.just(req);
    }

    private Mono<ServerResponse> handleError(Throwable error) {
        if (error instanceof ServerWebInputException) {
            return ServerResponse.badRequest().bodyValue(new ErrorResponse("Invalid request body"));
        }
        if (error instanceof InvalidBatchFileException invalid) {
            return ServerResponse.badRequest().bodyValue(new ErrorResponse(invalid.getMessage()));
        }
        if (error instanceof BackupNotFoundException notFound) {
            return ServerResponse.status(HttpStatus.NOT_FOUND)
                    .bodyValue(new ErrorResponse(notFound.getMessage()));
        }
        if (error instanceof SqlExecutionInfrastructureException) {
            log.error("Infrastructure error during restore operation", error);
            return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .bodyValue(new ErrorResponse("Internal server error"));
        }
        log.error("Unexpected error during restore operation", error);
        return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyValue(new ErrorResponse("Internal server error"));
    }
}
