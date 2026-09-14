package co.com.bancolombia.model.sql.exceptions;

public class SqlScriptNotFoundException extends RuntimeException {

    public SqlScriptNotFoundException(String message) {
        super(message);
    }
}
