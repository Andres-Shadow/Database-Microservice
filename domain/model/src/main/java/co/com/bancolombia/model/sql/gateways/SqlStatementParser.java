package co.com.bancolombia.model.sql.gateways;

import co.com.bancolombia.model.sql.SqlStatement;

import java.util.List;

public interface SqlStatementParser {

    List<SqlStatement> parse(String content);
}
