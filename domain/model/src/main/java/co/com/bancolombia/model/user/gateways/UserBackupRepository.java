package co.com.bancolombia.model.user.gateways;

import co.com.bancolombia.model.user.UserBackup;
import reactor.core.publisher.Mono;

public interface UserBackupRepository {

    Mono<UserBackup> save(UserBackup backup);

    Mono<UserBackup> findById(String backupId);
}
