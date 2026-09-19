package co.com.bancolombia.model.management.gateways;

import co.com.bancolombia.model.management.StorageFileDetail;
import co.com.bancolombia.model.management.StorageFileSummary;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface StorageFileManager {

    Flux<StorageFileSummary> listFiles();

    Mono<StorageFileDetail> getFileDetail(String key);

    Mono<Void> deleteFile(String key);
}