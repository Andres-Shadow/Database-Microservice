package co.com.bancolombia.api;

import co.com.bancolombia.model.sql.exceptions.SqlExecutionInfrastructureException;
import co.com.bancolombia.model.user.exceptions.BackupNotFoundException;
import co.com.bancolombia.usecase.management.BackupManagementUseCase;
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
public class BackupManagementHandler {

    private final BackupManagementUseCase useCase;

    public Mono<ServerResponse> listBackups(ServerRequest request) {
        return useCase.listBackups()
                .collectList()
                .flatMap(backups -> ServerResponse.ok().bodyValue(backups))
                .onErrorResume(this::handleError);
    }

    public Mono<ServerResponse> getBackup(ServerRequest request) {
        String backupId = request.pathVariable("backupId");
        return useCase.getBackup(backupId)
                .flatMap(backup -> ServerResponse.ok().bodyValue(backup))
                .onErrorResume(this::handleError);
    }

    public Mono<ServerResponse> deleteBackup(ServerRequest request) {
        String backupId = request.pathVariable("backupId");
        return useCase.deleteBackup(backupId)
                .then(ServerResponse.ok().bodyValue(new SuccessResponse("Backup deleted: " + backupId)))
                .onErrorResume(this::handleError);
    }

    private Mono<ServerResponse> handleError(Throwable error) {
        if (error instanceof BackupNotFoundException notFound) {
            return ServerResponse.status(HttpStatus.NOT_FOUND)
                    .bodyValue(new ErrorResponse(notFound.getMessage()));
        }
        if (error instanceof SqlExecutionInfrastructureException) {
            log.error("Infrastructure error in backup management", error);
            return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .bodyValue(new ErrorResponse("Internal server error"));
        }
        log.error("Unexpected error in backup management", error);
        return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyValue(new ErrorResponse("Internal server error"));
    }
}