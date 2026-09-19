package co.com.bancolombia.usecase.user;

import co.com.bancolombia.model.user.UserBackup;
import co.com.bancolombia.model.user.exceptions.BackupNotFoundException;
import co.com.bancolombia.model.user.gateways.UserBackupRepository;
import co.com.bancolombia.model.user.gateways.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import reactor.core.publisher.Mono;

import java.util.logging.Level;

@Log
@RequiredArgsConstructor
public class RestoreUserFromBackupUseCase {

    private final UserBackupRepository backupRepository;
    private final UserRepository userRepository;

    public Mono<String> execute(String backupId) {
        log.info("Restore from backup requested, backupId: " + backupId);

        return backupRepository.findById(backupId)
                .switchIfEmpty(Mono.error(new BackupNotFoundException("Backup not found: " + backupId)))
                .flatMap(backup -> userRepository.insertUser(backup.user())
                        .then(Mono.fromCallable(() -> {
                            log.info("User restored from backup: " + backupId);
                            return "User restored successfully from backup " + backupId;
                        })))
                .doOnError(error -> log.log(Level.SEVERE, "Restore from backup failed: " + backupId, error));
    }
}
