package co.com.bancolombia.dynamodb;

import co.com.bancolombia.model.management.ExecutionRecord;
import co.com.bancolombia.model.management.gateways.ExecutionHistoryRepository;
import co.com.bancolombia.model.sql.ExecutionStatus;
import co.com.bancolombia.model.sql.exceptions.SqlExecutionInfrastructureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.time.LocalDateTime;

@Repository
@Slf4j
public class DynamoDBExecutionHistoryAdapter implements ExecutionHistoryRepository {

    private final DynamoDbAsyncTable<ExecutionRecordEntity> table;

    public DynamoDBExecutionHistoryAdapter(DynamoDbEnhancedAsyncClient client) {
        this.table = client.table("execution_history", TableSchema.fromBean(ExecutionRecordEntity.class));
    }

    @Override
    public Mono<Void> save(ExecutionRecord record) {
        return Mono.fromFuture(table.putItem(toEntity(record)))
                .then()
                .doOnSuccess(v -> log.info("Execution record saved: {} ({})", record.executionId(), record.operationType()))
                .onErrorMap(error -> new SqlExecutionInfrastructureException(
                        "Unable to save execution record: " + error.getMessage(), error));
    }

    @Override
    public Flux<ExecutionRecord> findAll() {
        return Mono.from(table.scan())
                .flatMapMany(page -> Flux.fromIterable(page.items()))
                .map(this::toDomain)
                .onErrorMap(error -> new SqlExecutionInfrastructureException(
                        "Unable to list execution records: " + error.getMessage(), error));
    }

    @Override
    public Flux<ExecutionRecord> findByOperationType(String operationType) {
        return findAll()
                .filter(record -> operationType.equals(record.operationType()));
    }

    private ExecutionRecordEntity toEntity(ExecutionRecord record) {
        ExecutionRecordEntity entity = new ExecutionRecordEntity();
        entity.setExecutionId(record.executionId());
        entity.setOperationType(record.operationType());
        entity.setSourceFile(record.sourceFile());
        entity.setStatus(record.status().name());
        entity.setTotalEntries(record.totalEntries());
        entity.setSuccessfulEntries(record.successfulEntries());
        entity.setFailedEntries(record.failedEntries());
        entity.setExecutionTimeMs(record.executionTimeMs());
        entity.setExecutedAt(record.executedAt() != null ? record.executedAt().toString() : null);
        return entity;
    }

    private ExecutionRecord toDomain(ExecutionRecordEntity entity) {
        return new ExecutionRecord(
                entity.getExecutionId(),
                entity.getOperationType(),
                entity.getSourceFile(),
                ExecutionStatus.valueOf(entity.getStatus()),
                entity.getTotalEntries(),
                entity.getSuccessfulEntries(),
                entity.getFailedEntries(),
                entity.getExecutionTimeMs(),
                entity.getExecutedAt() != null ? LocalDateTime.parse(entity.getExecutedAt()) : null
        );
    }
}