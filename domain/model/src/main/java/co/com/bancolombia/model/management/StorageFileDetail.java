package co.com.bancolombia.model.management;

import java.time.Instant;

public record StorageFileDetail(
        String key,
        long size,
        Instant lastModified,
        String content) {
}