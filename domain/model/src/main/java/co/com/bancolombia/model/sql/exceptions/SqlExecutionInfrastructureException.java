package co.com.bancolombia.model.sql.exceptions;

public class SqlExecutionInfrastructureException extends RuntimeException {

    public SqlExecutionInfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
