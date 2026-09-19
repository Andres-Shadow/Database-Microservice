package co.com.bancolombia.model.management.gateways;

import co.com.bancolombia.model.user.UserBackup;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BackupManager {

    Flux<UserBackup> listBackups();

    Mono<UserBackup> getBackup(String backupId);

    Mono<Void> deleteBackup(String backupId);
}