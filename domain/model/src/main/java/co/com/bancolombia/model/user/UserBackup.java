package co.com.bancolombia.model.user;

import java.time.LocalDateTime;

public record UserBackup(
        String backupId,
        UserSnapshot user,
        LocalDateTime deletedAt) {
}
