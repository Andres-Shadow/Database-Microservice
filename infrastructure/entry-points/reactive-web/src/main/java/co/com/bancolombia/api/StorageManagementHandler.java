package co.com.bancolombia.api;

import co.com.bancolombia.model.sql.exceptions.SqlExecutionInfrastructureException;
import co.com.bancolombia.model.user.exceptions.BatchFileNotFoundException;
import co.com.bancolombia.usecase.management.StorageManagementUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class StorageManagementHandler {

    private final StorageManagementUseCase useCase;

    public Mono<ServerResponse> listFiles(ServerRequest request) {
        return useCase.listFiles()
                .collectList()
                .flatMap(files -> ServerResponse.ok().bodyValue(files))
                .onErrorResume(this::handleError);
    }

    public Mono<ServerResponse> getFileDetail(ServerRequest request) {
        String key = request.queryParam("key").orElse(null);
        if (key == null || key.isBlank()) {
            return ServerResponse.badRequest().bodyValue(new ErrorResponse("query param 'key' is required"));
        }

        return useCase.getFileDetail(key)
                .flatMap(detail -> ServerResponse.ok().bodyValue(detail))
                .onErrorResume(this::handleError);
    }

    public Mono<ServerResponse> deleteFile(ServerRequest request) {
        String key = request.queryParam("key").orElse(null);
        if (key == null || key.isBlank()) {
            return ServerResponse.badRequest().bodyValue(new ErrorResponse("query param 'key' is required"));
        }

        return useCase.deleteFile(key)
                .then(ServerResponse.ok().bodyValue(new SuccessResponse("File deleted: " + key)))
                .onErrorResume(this::handleError);
    }

    private Mono<ServerResponse> handleError(Throwable error) {
        if (error instanceof BatchFileNotFoundException notFound) {
            return ServerResponse.status(HttpStatus.NOT_FOUND)
                    .bodyValue(new ErrorResponse(notFound.getMessage()));
        }
        if (error instanceof SqlExecutionInfrastructureException) {
            log.error("Infrastructure error in storage management", error);
            return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .bodyValue(new ErrorResponse("Internal server error"));
        }
        log.error("Unexpected error in storage management", error);
        return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyValue(new ErrorResponse("Internal server error"));
    }
}