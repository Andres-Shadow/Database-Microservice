package co.com.bancolombia.usecase.management;

import co.com.bancolombia.model.management.StorageFileDetail;
import co.com.bancolombia.model.management.StorageFileSummary;
import co.com.bancolombia.model.management.gateways.StorageFileManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Log
@RequiredArgsConstructor
public class StorageManagementUseCase {

    private final StorageFileManager storageFileManager;

    public Flux<StorageFileSummary> listFiles() {
        log.info("Listing files from storage");
        return storageFileManager.listFiles();
    }

    public Mono<StorageFileDetail> getFileDetail(String key) {
        log.info("Getting file detail: " + key);
        return storageFileManager.getFileDetail(key);
    }

    public Mono<Void> deleteFile(String key) {
        log.info("Deleting file: " + key);
        return storageFileManager.deleteFile(key);
    }
}