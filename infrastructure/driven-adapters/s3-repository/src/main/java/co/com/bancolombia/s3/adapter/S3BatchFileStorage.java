package co.com.bancolombia.s3.adapter;

import co.com.bancolombia.model.sql.exceptions.SqlExecutionInfrastructureException;
import co.com.bancolombia.model.user.exceptions.BatchFileNotFoundException;
import co.com.bancolombia.model.user.gateways.BatchFileStorage;
import co.com.bancolombia.s3.config.model.S3ConnectionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Repository
@RequiredArgsConstructor
@Slf4j
public class S3BatchFileStorage implements BatchFileStorage {

    private final S3AsyncClient s3AsyncClient;
    private final S3ConnectionProperties properties;

    @Override
    public Mono<String> read(String fileName) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(properties.bucketName())
                .key(fileName)
                .build();

        return Mono.fromFuture(s3AsyncClient.getObject(request, AsyncResponseTransformer.toBytes()))
                .map(response -> response.asUtf8String())
                .doOnSuccess(content -> log.info("Batch file retrieved from S3: {}", fileName))
                .onErrorMap(error -> mapGetError(error, fileName));
    }

    @Override
    public Mono<String> saveResult(String sourceFileName, String content) {
        String resultKey = buildResultKey(sourceFileName);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucketName())
                .key(resultKey)
                .build();

        return Mono.fromFuture(s3AsyncClient.putObject(request, AsyncRequestBody.fromString(content)))
                .map(response -> resultKey)
                .doOnSuccess(key -> log.info("Batch result file stored in S3: {}", key))
                .onErrorMap(error -> new SqlExecutionInfrastructureException(
                        "Unable to store batch result file in S3: " + error.getMessage(), error));
    }

    @Override
    public Mono<Void> delete(String fileName) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.bucketName())
                .key(fileName)
                .build();

        return Mono.fromFuture(s3AsyncClient.deleteObject(request))
                .then()
                .doOnSuccess(v -> log.info("Batch file deleted from S3: {}", fileName))
                .onErrorMap(error -> new SqlExecutionInfrastructureException(
                        "Unable to delete batch file from S3: " + error.getMessage(), error));
    }

    private Throwable mapGetError(Throwable error, String fileName) {
        if (error instanceof NoSuchKeyException) {
            return new BatchFileNotFoundException("Batch file not found in S3: " + fileName);
        }
        return new SqlExecutionInfrastructureException(
                "Unable to read batch file from S3: " + error.getMessage(), error);
    }

    private String buildResultKey(String sourceFileName) {
        String baseName = sourceFileName;
        int lastDot = baseName.lastIndexOf('.');
        if (lastDot > 0) {
            baseName = baseName.substring(0, lastDot);
        }
        return baseName + "_result.txt";
    }
}
