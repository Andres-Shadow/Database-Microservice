package co.com.bancolombia;

import co.com.bancolombia.model.sql.ExecutionStatus;
import co.com.bancolombia.model.sql.gateways.SqlExecutor;
import co.com.bancolombia.model.sql.gateways.SqlScriptStorage;
import co.com.bancolombia.model.sql.gateways.SqlStatementParser;
import co.com.bancolombia.r2dbc.PostgresSqlExecutor;
import co.com.bancolombia.s3.adapter.S3SqlScriptStorage;
import co.com.bancolombia.s3.config.model.S3ConnectionProperties;
import co.com.bancolombia.usecase.sql.DefaultSqlStatementParser;
import co.com.bancolombia.usecase.sql.ExecuteSqlScriptUseCase;
import co.com.bancolombia.usecase.sql.SqlExecutionReportFormatter;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

@Testcontainers(disabledWithoutDocker = true)
class SqlExecutionIntegrationTest {

    private static final String BUCKET = "sql-execution";
    private static final String FILE_NAME = "approved/test.sql";
    private static final String RESULT_KEY = "results/test-result.txt";
    private static final String SCRIPT = """
            CREATE TABLE IF NOT EXISTS test_table (
                id BIGINT,
                name VARCHAR(100)
            );

            INSERT INTO test_table (id, name) VALUES (1, 'Andres');

            UPDATE test_table SET name = 'Andres Updated' WHERE id = 1;
            """;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("sql_execution")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3"))
            .withServices(S3);

    private static ExecuteSqlScriptUseCase useCase;
    private static DatabaseClient databaseClient;
    private static S3AsyncClient s3AsyncClient;

    @BeforeAll
    static void setUp() {
        s3AsyncClient = buildS3Client();

        StepVerifier.create(
                        Mono.fromFuture(s3AsyncClient.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build()))
                                .then(uploadScript()))
                .verifyComplete();

        S3ConnectionProperties properties = new S3ConnectionProperties(
                localstack.getEndpoint().toString(),
                localstack.getRegion(),
                BUCKET,
                localstack.getAccessKey(),
                localstack.getSecretKey(),
                "results");

        databaseClient = buildDatabaseClient();
        SqlScriptStorage storage = new S3SqlScriptStorage(s3AsyncClient, properties);
        SqlExecutor executor = new PostgresSqlExecutor(databaseClient);
        SqlStatementParser parser = new DefaultSqlStatementParser();
        SqlExecutionReportFormatter formatter = new SqlExecutionReportFormatter();

        useCase = new ExecuteSqlScriptUseCase(storage, parser, executor, formatter);
    }

    @Test
    void shouldExecuteScriptEndToEnd() {
        StepVerifier.create(useCase.execute(FILE_NAME))
                .assertNext(outcome -> {
                    assertThat(outcome.status()).isEqualTo(ExecutionStatus.SUCCESS);
                    assertThat(outcome.totalStatements()).isEqualTo(3);
                    assertThat(outcome.successfulStatements()).isEqualTo(3);
                    assertThat(outcome.failedStatements()).isZero();
                    assertThat(outcome.resultFile()).isEqualTo(RESULT_KEY);
                })
                .verifyComplete();

        StepVerifier.create(updatedRowCount())
                .expectNext(1)
                .verifyComplete();

        StepVerifier.create(resultFileContent())
                .assertNext(report -> assertThat(report)
                        .contains("Statement #1", "Statement #2", "Statement #3", "SUCCESS"))
                .verifyComplete();
    }

    private static Mono<Void> uploadScript() {
        return Mono.defer(() -> Mono.fromFuture(s3AsyncClient.putObject(
                        PutObjectRequest.builder().bucket(BUCKET).key(FILE_NAME).build(),
                        AsyncRequestBody.fromString(SCRIPT))))
                .then()
                .retryWhen(Retry.backoff(5, Duration.ofSeconds(1))
                        .filter(error -> error instanceof NoSuchBucketException));
    }

    private Mono<Integer> updatedRowCount() {
        return databaseClient.sql("SELECT COUNT(*) FROM test_table WHERE name = 'Andres Updated'")
                .map((row, meta) -> row.get(0, Integer.class))
                .one();
    }

    private Mono<String> resultFileContent() {
        return Mono.fromFuture(s3AsyncClient.getObject(
                        GetObjectRequest.builder().bucket(BUCKET).key(RESULT_KEY).build(),
                        AsyncResponseTransformer.toBytes()))
                .map(response -> response.asUtf8String());
    }

    private static S3AsyncClient buildS3Client() {
        return S3AsyncClient.builder()
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .endpointOverride(localstack.getEndpoint())
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private static DatabaseClient buildDatabaseClient() {
        PostgresqlConnectionFactory factory = new PostgresqlConnectionFactory(
                PostgresqlConnectionConfiguration.builder()
                        .host(postgres.getHost())
                        .port(postgres.getMappedPort(5432))
                        .database(postgres.getDatabaseName())
                        .username(postgres.getUsername())
                        .password(postgres.getPassword())
                        .build());
        return DatabaseClient.create(factory);
    }
}
