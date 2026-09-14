package co.com.bancolombia.usecase.sql;

import co.com.bancolombia.model.sql.SqlStatement;
import co.com.bancolombia.model.sql.exceptions.InvalidSqlScriptException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultSqlStatementParserTest {

    private final DefaultSqlStatementParser parser = new DefaultSqlStatementParser();

    @Test
    void shouldSplitMultipleStatementsInOrder() {
        String content = "UPDATE customers SET status = 'ACTIVE' WHERE id = 10;\n"
                + "UPDATE customers SET status = 'ACTIVE' WHERE id = 20;\n"
                + "DELETE FROM customer_sessions WHERE customer_id = 20;";

        List<SqlStatement> statements = parser.parse(content);

        assertThat(statements).hasSize(3);
        assertThat(statements).extracting(SqlStatement::sequence).containsExactly(1, 2, 3);
    }

    @Test
    void shouldNotSplitSemicolonInsideSingleQuotedString() {
        List<SqlStatement> statements = parser.parse("INSERT INTO t (v) VALUES ('a;b');");

        assertThat(statements).hasSize(1);
        assertThat(statements.get(0).sql()).contains("'a;b'");
    }

    @Test
    void shouldNotSplitSemicolonInsideDoubleQuotedIdentifier() {
        List<SqlStatement> statements = parser.parse("CREATE TABLE \"weird;name\" (id INT);");

        assertThat(statements).hasSize(1);
        assertThat(statements.get(0).sql()).contains("\"weird;name\"");
    }

    @Test
    void shouldNotSplitSemicolonInsideDollarQuotedBlock() {
        List<SqlStatement> statements = parser.parse("DO $$ BEGIN PERFORM 'x'; END $$; SELECT 1;");

        assertThat(statements).hasSize(2);
        assertThat(statements.get(0).sql()).contains("DO $$");
    }

    @Test
    void shouldNotSplitSemicolonInsideLineComment() {
        List<SqlStatement> statements = parser.parse("SELECT 1; -- a; comment\nSELECT 2;");

        assertThat(statements).hasSize(2);
    }

    @Test
    void shouldNotSplitSemicolonInsideBlockComment() {
        List<SqlStatement> statements = parser.parse("SELECT 1; /* a; comment */ SELECT 2;");

        assertThat(statements).hasSize(2);
    }

    @Test
    void shouldIgnoreEmptyStatementsBetweenSemicolons() {
        List<SqlStatement> statements = parser.parse("SELECT 1;;;SELECT 2;");

        assertThat(statements).hasSize(2);
    }

    @Test
    void shouldThrowWhenContentIsEmpty() {
        assertThatThrownBy(() -> parser.parse(""))
                .isInstanceOf(InvalidSqlScriptException.class);
    }

    @Test
    void shouldThrowWhenContentIsBlank() {
        assertThatThrownBy(() -> parser.parse("   \n\t "))
                .isInstanceOf(InvalidSqlScriptException.class);
    }

    @Test
    void shouldThrowWhenSingleQuoteIsUnterminated() {
        assertThatThrownBy(() -> parser.parse("SELECT 'unterminated"))
                .isInstanceOf(InvalidSqlScriptException.class);
    }

    @Test
    void shouldThrowWhenDollarQuoteIsUnterminated() {
        assertThatThrownBy(() -> parser.parse("DO $$ BEGIN END"))
                .isInstanceOf(InvalidSqlScriptException.class);
    }
}
