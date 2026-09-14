package co.com.bancolombia.usecase.sql;

import co.com.bancolombia.model.sql.SqlStatement;
import co.com.bancolombia.model.sql.exceptions.InvalidSqlScriptException;
import co.com.bancolombia.model.sql.gateways.SqlStatementParser;

import java.util.ArrayList;
import java.util.List;

public class DefaultSqlStatementParser implements SqlStatementParser {

    @Override
    public List<SqlStatement> parse(String content) {
        if (content == null || content.isBlank()) {
            throw new InvalidSqlScriptException("SQL script content is empty");
        }

        List<SqlStatement> statements = new ArrayList<>();
        int sequence = 1;
        for (String part : split(content)) {
            String sql = part.trim();
            if (!sql.isEmpty()) {
                statements.add(new SqlStatement(sequence++, sql));
            }
        }

        if (statements.isEmpty()) {
            throw new InvalidSqlScriptException("SQL script does not contain any statement");
        }
        return statements;
    }

    private List<String> split(String content) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int index = 0;

        while (index < content.length()) {
            char character = content.charAt(index);
            char next = index + 1 < content.length() ? content.charAt(index + 1) : '\0';

            if (character == '-' && next == '-') {
                int end = readLineComment(content, index);
                current.append(content, index, end);
                index = end;
            } else if (character == '/' && next == '*') {
                int end = readBlockComment(content, index);
                current.append(content, index, end);
                index = end;
            } else if (character == '\'') {
                int end = readSingleQuoted(content, index);
                current.append(content, index, end);
                index = end;
            } else if (character == '"') {
                int end = readDoubleQuoted(content, index);
                current.append(content, index, end);
                index = end;
            } else if (character == '$') {
                int end = readDollarQuoted(content, index);
                if (end == index) {
                    current.append(character);
                    index++;
                } else {
                    current.append(content, index, end);
                    index = end;
                }
            } else if (character == ';') {
                statements.add(current.toString());
                current.setLength(0);
                index++;
            } else {
                current.append(character);
                index++;
            }
        }

        String last = current.toString().trim();
        if (!last.isEmpty()) {
            statements.add(last);
        }
        return statements;
    }

    private int readLineComment(String content, int start) {
        int end = content.indexOf('\n', start);
        return end < 0 ? content.length() : end;
    }

    private int readBlockComment(String content, int start) {
        int depth = 1;
        int index = start + 2;
        while (index < content.length() && depth > 0) {
            if (index + 1 < content.length() && content.charAt(index) == '/' && content.charAt(index + 1) == '*') {
                depth++;
                index += 2;
            } else if (index + 1 < content.length() && content.charAt(index) == '*' && content.charAt(index + 1) == '/') {
                depth--;
                index += 2;
            } else {
                index++;
            }
        }
        if (depth > 0) {
            throw new InvalidSqlScriptException("Unterminated block comment starting at index " + start);
        }
        return index;
    }

    private int readSingleQuoted(String content, int start) {
        int index = start + 1;
        while (index < content.length()) {
            if (content.charAt(index) == '\'') {
                if (index + 1 < content.length() && content.charAt(index + 1) == '\'') {
                    index += 2;
                } else {
                    return index + 1;
                }
            } else {
                index++;
            }
        }
        throw new InvalidSqlScriptException("Unterminated single-quoted string starting at index " + start);
    }

    private int readDoubleQuoted(String content, int start) {
        int index = start + 1;
        while (index < content.length()) {
            if (content.charAt(index) == '"') {
                if (index + 1 < content.length() && content.charAt(index + 1) == '"') {
                    index += 2;
                } else {
                    return index + 1;
                }
            } else {
                index++;
            }
        }
        throw new InvalidSqlScriptException("Unterminated double-quoted identifier starting at index " + start);
    }

    private int readDollarQuoted(String content, int start) {
        int index = start + 1;
        StringBuilder tag = new StringBuilder();
        while (index < content.length() && isTagCharacter(content.charAt(index))) {
            tag.append(content.charAt(index));
            index++;
        }
        if (index < content.length() && content.charAt(index) == '$') {
            String delimiter = "$" + tag + "$";
            int close = content.indexOf(delimiter, index + 1);
            if (close < 0) {
                throw new InvalidSqlScriptException("Unterminated dollar-quoted string starting at index " + start);
            }
            return close + delimiter.length();
        }
        return start;
    }

    private boolean isTagCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }
}
