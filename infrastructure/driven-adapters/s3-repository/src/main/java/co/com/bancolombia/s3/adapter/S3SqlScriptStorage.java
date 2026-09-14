package co.com.bancolombia.s3.adapter;

import co.com.bancolombia.model.sql.SqlScript;
import co.com.bancolombia.model.sql.exceptions.SqlExecutionInfrastructureException;
import co.com.bancolombia.model.sql.exceptions.SqlScriptNotFoundException;
import co.com.bancolombia.model.sql.gateways.SqlScriptStorage;
import co.com.bancolombia.s3.config.model.S3ConnectionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Repository
@RequiredArgsConstructor
@Slf4j
public class S3SqlScriptStorage implements SqlScriptStorage {

    private final S3AsyncClient s3AsyncClient;
    private final S3ConnectionProperties properties;

    @Override
    public Mono<SqlScript> get(String fileName) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(properties.bucketName())
                .key(fileName)
                .build();

        return Mono.fromFuture(s3AsyncClient.getObject(request, AsyncResponseTransformer.toBytes()))
                .map(response -> new SqlScript(fileName, response.asUtf8String()))
                .doOnSuccess(script -> log.info("SQL script retrieved from S3: {}", fileName))
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
                .doOnSuccess(key -> log.info("Result file stored in S3: {}", key))
                .onErrorMap(error -> new SqlExecutionInfrastructureException(
                        "Unable to store result file in S3: " + error.getMessage(), error));
    }

    private Throwable mapGetError(Throwable error, String fileName) {
        if (error instanceof NoSuchKeyException) {
            return new SqlScriptNotFoundException("SQL script not found in S3: " + fileName);
        }
        return new SqlExecutionInfrastructureException(
                "Unable to read SQL script from S3: " + error.getMessage(), error);
    }

    private String buildResultKey(String sourceFileName) {
        return properties.resultPrefix() + "/" + extractBaseName(sourceFileName) + "-result.txt";
    }

    private String extractBaseName(String fileName) {
        String name = fileName;
        int lastSeparator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSeparator >= 0) {
            name = name.substring(lastSeparator + 1);
        }
        if (name.toLowerCase().endsWith(".sql")) {
            name = name.substring(0, name.length() - ".sql".length());
        }
        return name;
    }
}
