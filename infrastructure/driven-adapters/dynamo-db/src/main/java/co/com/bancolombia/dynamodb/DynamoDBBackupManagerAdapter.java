package co.com.bancolombia.dynamodb;

import co.com.bancolombia.model.management.gateways.BackupManager;
import co.com.bancolombia.model.sql.exceptions.SqlExecutionInfrastructureException;
import co.com.bancolombia.model.user.UserBackup;
import co.com.bancolombia.model.user.UserSnapshot;
import co.com.bancolombia.model.user.exceptions.BackupNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Repository
@Slf4j
public class DynamoDBBackupManagerAdapter implements BackupManager {

    private final DynamoDbAsyncTable<UserBackupEntity> table;

    public DynamoDBBackupManagerAdapter(DynamoDbEnhancedAsyncClient client) {
        this.table = client.table("user_backups", TableSchema.fromBean(UserBackupEntity.class));
    }

    @Override
    public Flux<UserBackup> listBackups() {
        return Mono.from(table.scan())
                .flatMapMany(page -> Flux.fromIterable(page.items()))
                .map(this::toDomain)
                .onErrorMap(error -> new SqlExecutionInfrastructureException(
                        "Unable to list backups from DynamoDB: " + error.getMessage(), error));
    }

    @Override
    public Mono<UserBackup> getBackup(String backupId) {
        return Mono.fromFuture(table.getItem(Key.builder()
                        .partitionValue(backupId)
                        .build()))
                .map(entity -> {
                    if (entity == null) {
                        throw new BackupNotFoundException("Backup not found: " + backupId);
                    }
                    return toDomain(entity);
                })
                .onErrorMap(error -> !(error instanceof BackupNotFoundException),
                        error -> new SqlExecutionInfrastructureException(
                                "Unable to get backup from DynamoDB: " + error.getMessage(), error));
    }

    @Override
    public Mono<Void> deleteBackup(String backupId) {
        return Mono.fromFuture(table.deleteItem(Key.builder()
                        .partitionValue(backupId)
                        .build()))
                .then()
                .doOnSuccess(v -> log.info("Backup deleted from DynamoDB: {}", backupId))
                .onErrorMap(error -> new SqlExecutionInfrastructureException(
                        "Unable to delete backup from DynamoDB: " + error.getMessage(), error));
    }

    private UserBackup toDomain(UserBackupEntity entity) {
        return new UserBackup(
                entity.getBackupId(),
                new UserSnapshot(
                        entity.getNombre(),
                        entity.getEmail(),
                        entity.getEdad(),
                        entity.getActivo(),
                        entity.getSalario() != null ? new BigDecimal(entity.getSalario()) : null,
                        entity.getFechaRegistro() != null ? LocalDateTime.parse(entity.getFechaRegistro()) : null
                ),
                entity.getDeletedAt() != null ? LocalDateTime.parse(entity.getDeletedAt()) : null
        );
    }
}