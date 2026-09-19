package co.com.bancolombia.model.management;

import java.time.Instant;

public record StorageFileSummary(
        String key,
        long size,
        Instant lastModified) {
}