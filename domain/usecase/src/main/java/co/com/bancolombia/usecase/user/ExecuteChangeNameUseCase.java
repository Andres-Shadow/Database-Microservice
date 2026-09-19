package co.com.bancolombia.usecase.user;

import co.com.bancolombia.model.management.ExecutionRecord;
import co.com.bancolombia.model.management.gateways.ExecutionHistoryRepository;
import co.com.bancolombia.model.management.gateways.OperationMetrics;
import co.com.bancolombia.model.sql.ExecutionStatus;
import co.com.bancolombia.model.user.*;
import co.com.bancolombia.model.user.exceptions.BatchFileNotFoundException;
import co.com.bancolombia.model.user.gateways.BatchFileStorage;
import co.com.bancolombia.model.user.gateways.ChangeNameParser;
import co.com.bancolombia.model.user.gateways.UserRepository;
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
public class ExecuteChangeNameUseCase {

    private static final String SOURCE_FILE = "change_name.txt";

    private final BatchFileStorage storage;
    private final ChangeNameParser parser;
    private final UserRepository userRepository;
    private final BatchOperationReportFormatter formatter;
    private final ExecutionHistoryRepository historyRepository;
    private final OperationMetrics metrics;

    public Mono<BatchOperationOutcome> execute() {
        long start = System.nanoTime();
        log.info("Change name batch operation requested");

        return storage.read(SOURCE_FILE)
                .switchIfEmpty(Mono.error(new BatchFileNotFoundException("File not found: " + SOURCE_FILE)))
                .flatMap(content -> {
                    List<ChangeNameEntry> entries = parser.parse(content);
                    log.info("Parsed " + entries.size() + " change name entries");
                    return executeEntries(entries, start);
                })
                .doOnError(error -> log.log(Level.SEVERE, "Change name batch operation failed", error));
    }

    private Mono<BatchOperationOutcome> executeEntries(List<ChangeNameEntry> entries, long start) {
        return Flux.fromIterable(entries)
                .concatMap(this::executeEntry)
                .collectList()
                .map(results -> buildResult(results, start))
                .flatMap(this::persistResult)
                .flatMap(outcome -> recordHistory(outcome, start).thenReturn(outcome));
    }

    private Mono<BatchEntryResult> executeEntry(ChangeNameEntry entry) {
        long start = System.nanoTime();
        String operation = "UPDATE usuarios SET nombre = '%s' WHERE id = %d".formatted(entry.newName(), entry.id());

        return userRepository.updateUser(entry.id(), entry.newName())
                .map(rows -> new BatchEntryResult(
                        entry.id(), operation, ExecutionStatus.SUCCESS, rows, elapsedMs(start), null))
                .onErrorResume(error -> {
                    log.warning("Change name entry failed for id=" + entry.id() + ": " + error.getMessage());
                    return Mono.just(new BatchEntryResult(
                            entry.id(), operation, ExecutionStatus.FAILED, null, elapsedMs(start), error.getMessage()));
                });
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

    private Mono<Void> recordHistory(BatchOperationOutcome outcome, long start) {
        metrics.recordExecution("CHANGE_NAME", outcome.status().name(), elapsedMs(start));

        ExecutionRecord record = new ExecutionRecord(
                UUID.randomUUID().toString(),
                "CHANGE_NAME",
                outcome.sourceFile(),
                outcome.status(),
                outcome.totalEntries(),
                outcome.successfulEntries(),
                outcome.failedEntries(),
                outcome.totalExecutionTimeMs(),
                LocalDateTime.now());

        return historyRepository.save(record)
                .doOnError(error -> log.warning("Failed to save execution history: " + error.getMessage()))
                .onErrorResume(error -> Mono.empty());
    }

    private long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}