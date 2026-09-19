package co.com.bancolombia.usecase.user;

import co.com.bancolombia.model.sql.ExecutionStatus;
import co.com.bancolombia.model.user.*;
import co.com.bancolombia.model.user.exceptions.BatchFileNotFoundException;
import co.com.bancolombia.model.user.gateways.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

@Log
@RequiredArgsConstructor
public class ExecuteDeleteUserUseCase {

    private static final String SOURCE_FILE = "delete_user.txt";

    private final BatchFileStorage storage;
    private final DeleteUserParser parser;
    private final UserRepository userRepository;
    private final UserBackupRepository backupRepository;
    private final BatchOperationReportFormatter formatter;

    public Mono<BatchOperationOutcome> execute() {
        long start = System.nanoTime();
        log.info("Delete user batch operation requested");

        return storage.read(SOURCE_FILE)
                .switchIfEmpty(Mono.error(new BatchFileNotFoundException("File not found: " + SOURCE_FILE)))
                .flatMap(content -> {
                    List<DeleteUserEntry> entries = parser.parse(content);
                    log.info("Parsed " + entries.size() + " delete user entries");
                    return executeEntries(entries, start);
                })
                .doOnError(error -> log.log(Level.SEVERE, "Delete user batch operation failed", error));
    }

    private Mono<BatchOperationOutcome> executeEntries(List<DeleteUserEntry> entries, long start) {
        return Flux.fromIterable(entries)
                .concatMap(this::executeEntry)
                .collectList()
                .map(results -> buildResult(results, start))
                .flatMap(this::persistResult);
    }

    private Mono<BatchEntryResult> executeEntry(DeleteUserEntry entry) {
        long start = System.nanoTime();
        String operation = "DELETE FROM usuarios WHERE id = %d AND nombre = '%s' AND email = '%s'"
                .formatted(entry.id(), entry.name(), entry.email());

        return userRepository.findUser(entry.id(), entry.name(), entry.email())
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "User not found for backup: id=%d, nombre=%s, email=%s"
                                .formatted(entry.id(), entry.name(), entry.email()))))
                .flatMap(snapshot -> backupToDynamo(snapshot, entry))
                .flatMap(backup -> userRepository.deleteUser(entry.id(), entry.name(), entry.email()))
                .map(rows -> new BatchEntryResult(
                        entry.id(), operation, ExecutionStatus.SUCCESS, rows, elapsedMs(start), null))
                .onErrorResume(error -> {
                    log.warning("Delete user entry failed for id=" + entry.id() + ": " + error.getMessage());
                    return Mono.just(new BatchEntryResult(
                            entry.id(), operation, ExecutionStatus.FAILED, null, elapsedMs(start), error.getMessage()));
                });
    }

    private Mono<UserBackup> backupToDynamo(UserSnapshot snapshot, DeleteUserEntry entry) {
        UserBackup backup = new UserBackup(
                UUID.randomUUID().toString(),
                snapshot,
                LocalDateTime.now());
        return backupRepository.save(backup)
                .doOnSuccess(b -> log.info("Backup saved for user id=" + entry.id() + ", backupId=" + b.backupId()));
    }

    private BatchOperationResult buildResult(List<BatchEntryResult> entries, long start) {
        ExecutionStatus status = entries.stream().allMatch(e -> e.status() == ExecutionStatus.SUCCESS)
                ? ExecutionStatus.SUCCESS
                : ExecutionStatus.FAILED;
        return new BatchOperationResult(SOURCE_FILE, status, entries, elapsedMs(start));
    }

    private Mono<BatchOperationOutcome> persistResult(BatchOperationResult result) {
        String report = formatter.format(result);
        return storage.delete(SOURCE_FILE)
                .then(storage.saveResult(SOURCE_FILE, report))
                .map(resultFile -> {
                    log.info("Result file generated: " + resultFile);
                    return BatchOperationOutcome.from(result, resultFile);
                });
    }

    private long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
