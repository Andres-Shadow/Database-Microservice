package co.com.bancolombia.usecase.user;

import co.com.bancolombia.model.user.ChangeNameEntry;
import co.com.bancolombia.model.user.exceptions.InvalidBatchFileException;
import co.com.bancolombia.model.user.gateways.ChangeNameParser;

import java.util.ArrayList;
import java.util.List;

public class DefaultChangeNameParser implements ChangeNameParser {

    private static final String DELIMITER = ";";

    @Override
    public List<ChangeNameEntry> parse(String content) {
        if (content == null || content.isBlank()) {
            throw new InvalidBatchFileException("Batch file content is empty");
        }

        List<ChangeNameEntry> entries = new ArrayList<>();
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

    private ChangeNameEntry parseLine(String line, int lineNumber) {
        String[] parts = line.split(DELIMITER, -1);
        if (parts.length != 2) {
            throw new InvalidBatchFileException(
                    "Invalid format at line %d: expected 'id;newName', got '%s'".formatted(lineNumber, line));
        }

        String idPart = parts[0].trim();
        String newName = parts[1].trim();

        if (idPart.isEmpty() || newName.isEmpty()) {
            throw new InvalidBatchFileException(
                    "Invalid entry at line %d: id and newName must not be empty".formatted(lineNumber));
        }

        try {
            return new ChangeNameEntry(Integer.parseInt(idPart), newName);
        } catch (NumberFormatException e) {
            throw new InvalidBatchFileException(
                    "Invalid id at line %d: '%s' is not a valid integer".formatted(lineNumber, idPart));
        }
    }
}
