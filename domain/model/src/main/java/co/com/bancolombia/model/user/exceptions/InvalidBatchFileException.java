package co.com.bancolombia.model.user.exceptions;

public class InvalidBatchFileException extends RuntimeException {

    public InvalidBatchFileException(String message) {
        super(message);
    }
}
