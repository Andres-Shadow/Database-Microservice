package co.com.bancolombia.dynamodb;

import co.com.bancolombia.model.sql.exceptions.SqlExecutionInfrastructureException;
import co.com.bancolombia.model.user.UserBackup;
import co.com.bancolombia.model.user.UserSnapshot;
import co.com.bancolombia.model.user.exceptions.BackupNotFoundException;
import co.com.bancolombia.model.user.gateways.UserBackupRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Repository
@Slf4j
public class DynamoDBUserBackupRepository implements UserBackupRepository {

    private final DynamoDbAsyncTable<UserBackupEntity> table;

    public DynamoDBUserBackupRepository(DynamoDbEnhancedAsyncClient client) {
        this.table = client.table("user_backups", TableSchema.fromBean(UserBackupEntity.class));
    }

    @Override
    public Mono<UserBackup> save(UserBackup backup) {
        return Mono.fromFuture(table.putItem(toEntity(backup)))
                .thenReturn(backup)
                .doOnSuccess(b -> log.info("Backup saved to DynamoDB: {}", b.backupId()))
                .onErrorMap(error -> new SqlExecutionInfrastructureException(
                        "Unable to save backup to DynamoDB: " + error.getMessage(), error));
    }

    @Override
    public Mono<UserBackup> findById(String backupId) {
        return Mono.fromFuture(table.getItem(Key.builder()
                        .partitionValue(backupId)
                        .build()))
                .map(entity -> {
                    if (entity == null) {
                        throw new BackupNotFoundException("Backup not found in DynamoDB: " + backupId);
                    }
                    return toDomain(entity);
                })
                .onErrorMap(error -> !(error instanceof BackupNotFoundException)
                        && !(error instanceof ResourceNotFoundException),
                        error -> new SqlExecutionInfrastructureException(
                                "Unable to read backup from DynamoDB: " + error.getMessage(), error));
    }

    private UserBackupEntity toEntity(UserBackup backup) {
        UserBackupEntity entity = new UserBackupEntity();
        entity.setBackupId(backup.backupId());
        entity.setNombre(backup.user().nombre());
        entity.setEmail(backup.user().email());
        entity.setEdad(backup.user().edad());
        entity.setActivo(backup.user().activo());
        entity.setSalario(backup.user().salario() != null ? backup.user().salario().toPlainString() : null);
        entity.setFechaRegistro(backup.user().fechaRegistro() != null ? backup.user().fechaRegistro().toString() : null);
        entity.setDeletedAt(backup.deletedAt() != null ? backup.deletedAt().toString() : null);
        return entity;
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
