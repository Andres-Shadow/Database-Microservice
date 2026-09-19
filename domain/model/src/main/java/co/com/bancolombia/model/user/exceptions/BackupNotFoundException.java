package co.com.bancolombia.model.user.exceptions;

public class BackupNotFoundException extends RuntimeException {

    public BackupNotFoundException(String message) {
        super(message);
    }
}
