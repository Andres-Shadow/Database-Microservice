package co.com.bancolombia.usecase.user;

import co.com.bancolombia.model.management.ExecutionRecord;
import co.com.bancolombia.model.management.gateways.ExecutionHistoryRepository;
import co.com.bancolombia.model.management.gateways.OperationMetrics;
import co.com.bancolombia.model.sql.ExecutionStatus;
import co.com.bancolombia.model.user.exceptions.BackupNotFoundException;
import co.com.bancolombia.model.user.gateways.UserBackupRepository;
import co.com.bancolombia.model.user.gateways.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Level;

@Log
@RequiredArgsConstructor
public class RestoreUserFromBackupUseCase {

    private final UserBackupRepository backupRepository;
    private final UserRepository userRepository;
    private final ExecutionHistoryRepository historyRepository;
    private final OperationMetrics metrics;

    public Mono<String> execute(String backupId) {
        long start = System.nanoTime();
        log.info("Restore from backup requested, backupId: " + backupId);

        return backupRepository.findById(backupId)
                .switchIfEmpty(Mono.error(new BackupNotFoundException("Backup not found: " + backupId)))
                .flatMap(backup -> userRepository.insertUser(backup.user())
                        .then(Mono.fromCallable(() -> {
                            log.info("User restored from backup: " + backupId);
                            return "User restored successfully from backup " + backupId;
                        })))
                .flatMap(message -> recordHistory(backupId, ExecutionStatus.SUCCESS, start)
                        .thenReturn(message))
                .doOnError(error -> log.log(Level.SEVERE, "Restore from backup failed: " + backupId, error));
    }

    private Mono<Void> recordHistory(String backupId, ExecutionStatus status, long start) {
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        metrics.recordExecution("RESTORE_USER", status.name(), durationMs);
        metrics.recordBackupOperation("restored");

        ExecutionRecord record = new ExecutionRecord(
                UUID.randomUUID().toString(),
                "RESTORE_USER",
                backupId,
                status,
                1,
                status == ExecutionStatus.SUCCESS ? 1 : 0,
                status == ExecutionStatus.FAILED ? 1 : 0,
                durationMs,
                LocalDateTime.now());

        return historyRepository.save(record)
                .doOnError(error -> log.warning("Failed to save execution history: " + error.getMessage()))
                .onErrorResume(error -> Mono.empty());
    }
}