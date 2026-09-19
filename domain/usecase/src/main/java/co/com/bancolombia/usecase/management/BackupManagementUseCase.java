package co.com.bancolombia.usecase.management;

import co.com.bancolombia.model.management.gateways.BackupManager;
import co.com.bancolombia.model.user.UserBackup;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Log
@RequiredArgsConstructor
public class BackupManagementUseCase {

    private final BackupManager backupManager;

    public Flux<UserBackup> listBackups() {
        log.info("Listing all backups");
        return backupManager.listBackups();
    }

    public Mono<UserBackup> getBackup(String backupId) {
        log.info("Getting backup: " + backupId);
        return backupManager.getBackup(backupId);
    }

    public Mono<Void> deleteBackup(String backupId) {
        log.info("Deleting backup: " + backupId);
        return backupManager.deleteBackup(backupId);
    }
}