package co.com.bancolombia.model.management.gateways;

import co.com.bancolombia.model.management.ExecutionRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ExecutionHistoryRepository {

    Mono<Void> save(ExecutionRecord record);

    Flux<ExecutionRecord> findAll();

    Flux<ExecutionRecord> findByOperationType(String operationType);
}