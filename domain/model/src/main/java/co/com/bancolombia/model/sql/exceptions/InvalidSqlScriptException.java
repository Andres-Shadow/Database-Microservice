package co.com.bancolombia.model.sql.exceptions;

public class InvalidSqlScriptException extends RuntimeException {

    public InvalidSqlScriptException(String message) {
        super(message);
    }
}
