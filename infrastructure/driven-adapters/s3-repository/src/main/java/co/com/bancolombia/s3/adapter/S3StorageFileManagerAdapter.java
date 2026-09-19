package co.com.bancolombia.s3.adapter;

import co.com.bancolombia.model.management.StorageFileDetail;
import co.com.bancolombia.model.management.StorageFileSummary;
import co.com.bancolombia.model.management.gateways.StorageFileManager;
import co.com.bancolombia.model.sql.exceptions.SqlExecutionInfrastructureException;
import co.com.bancolombia.model.user.exceptions.BatchFileNotFoundException;
import co.com.bancolombia.s3.config.model.S3ConnectionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;

@Repository
@RequiredArgsConstructor
@Slf4j
public class S3StorageFileManagerAdapter implements StorageFileManager {

    private final S3AsyncClient s3AsyncClient;
    private final S3ConnectionProperties properties;

    @Override
    public Flux<StorageFileSummary> listFiles() {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(properties.bucketName())
                .build();

        return Mono.fromFuture(s3AsyncClient.listObjectsV2(request))
                .flatMapMany(response -> Flux.fromIterable(response.contents()))
                .map(obj -> new StorageFileSummary(obj.key(), obj.size(), obj.lastModified()))
                .onErrorMap(error -> new SqlExecutionInfrastructureException(
                        "Unable to list files from S3: " + error.getMessage(), error));
    }

    @Override
    public Mono<StorageFileDetail> getFileDetail(String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(properties.bucketName())
                .key(key)
                .build();

        return Mono.fromFuture(s3AsyncClient.getObject(request, AsyncResponseTransformer.toBytes()))
                .map(response -> new StorageFileDetail(
                        key,
                        response.response().contentLength(),
                        response.response().lastModified(),
                        response.asUtf8String()))
                .doOnSuccess(detail -> log.info("File detail retrieved: {}", key))
                .onErrorMap(error -> mapGetError(error, key));
    }

    @Override
    public Mono<Void> deleteFile(String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.bucketName())
                .key(key)
                .build();

        return Mono.fromFuture(s3AsyncClient.deleteObject(request))
                .then()
                .doOnSuccess(v -> log.info("File deleted from S3: {}", key))
                .onErrorMap(error -> new SqlExecutionInfrastructureException(
                        "Unable to delete file from S3: " + error.getMessage(), error));
    }

    private Throwable mapGetError(Throwable error, String key) {
        if (error instanceof NoSuchKeyException) {
            return new BatchFileNotFoundException("File not found in S3: " + key);
        }
        return new SqlExecutionInfrastructureException(
                "Unable to read file from S3: " + error.getMessage(), error);
    }
}