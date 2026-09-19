package co.com.bancolombia.model.user.exceptions;

public class BatchFileNotFoundException extends RuntimeException {

    public BatchFileNotFoundException(String message) {
        super(message);
    }
}
