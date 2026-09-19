package co.com.bancolombia.api;

import co.com.bancolombia.model.management.gateways.ExecutionHistoryRepository;
import co.com.bancolombia.model.sql.exceptions.SqlExecutionInfrastructureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExecutionHistoryHandler {

    private final ExecutionHistoryRepository historyRepository;

    public Mono<ServerResponse> listHistory(ServerRequest request) {
        String type = request.queryParam("type").orElse(null);

        var source = (type != null && !type.isBlank())
                ? historyRepository.findByOperationType(type)
                : historyRepository.findAll();

        return source.collectList()
                .flatMap(records -> ServerResponse.ok().bodyValue(records))
                .onErrorResume(this::handleError);
    }

    private Mono<ServerResponse> handleError(Throwable error) {
        if (error instanceof SqlExecutionInfrastructureException) {
            log.error("Infrastructure error in execution history", error);
            return ServerResponse.status(500).bodyValue(new ErrorResponse("Internal server error"));
        }
        log.error("Unexpected error in execution history", error);
        return ServerResponse.status(500).bodyValue(new ErrorResponse("Internal server error"));
    }
}