package co.com.bancolombia.usecase.user;

import co.com.bancolombia.model.user.DeleteUserEntry;
import co.com.bancolombia.model.user.exceptions.InvalidBatchFileException;
import co.com.bancolombia.model.user.gateways.DeleteUserParser;

import java.util.ArrayList;
import java.util.List;

public class DefaultDeleteUserParser implements DeleteUserParser {

    private static final String DELIMITER = ";";

    @Override
    public List<DeleteUserEntry> parse(String content) {
        if (content == null || content.isBlank()) {
            throw new InvalidBatchFileException("Batch file content is empty");
        }

        List<DeleteUserEntry> entries = new ArrayList<>();
        String[] lines = content.split("\\R");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            entries.add(parseLine(line, i + 1));
        }

        if (entries.isEmpty()) {
            throw new InvalidBatchFileException("Batch file does not contain any entry");
        }
        return entries;
    }

    private DeleteUserEntry parseLine(String line, int lineNumber) {
        String[] parts = line.split(DELIMITER, -1);
        if (parts.length != 3) {
            throw new InvalidBatchFileException(
                    "Invalid format at line %d: expected 'id;name;email', got '%s'".formatted(lineNumber, line));
        }

        String idPart = parts[0].trim();
        String name = parts[1].trim();
        String email = parts[2].trim();

        if (idPart.isEmpty() || name.isEmpty() || email.isEmpty()) {
            throw new InvalidBatchFileException(
                    "Invalid entry at line %d: id, name and email must not be empty".formatted(lineNumber));
        }

        try {
            return new DeleteUserEntry(Integer.parseInt(idPart), name, email);
        } catch (NumberFormatException e) {
            throw new InvalidBatchFileException(
                    "Invalid id at line %d: '%s' is not a valid integer".formatted(lineNumber, idPart));
        }
    }
}
