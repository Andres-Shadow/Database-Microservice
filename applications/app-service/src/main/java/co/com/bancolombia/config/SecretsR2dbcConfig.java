package co.com.bancolombia.config;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerAsyncClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.Map;

@Configuration
public class SecretsR2dbcConfig {

    @Bean
    public ConnectionFactory connectionFactory(DatabaseSecretProperties properties) {
        Map<String, Object> credentials = fetchSecret(properties);

        return new PostgresqlConnectionFactory(
                PostgresqlConnectionConfiguration.builder()
                        .host((String) credentials.get("host"))
                        .port(((Number) credentials.get("port")).intValue())
                        .database((String) credentials.get("database"))
                        .username((String) credentials.get("username"))
                        .password((String) credentials.get("password"))
                        .build());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchSecret(DatabaseSecretProperties properties) {
        var builder = SecretsManagerAsyncClient.builder()
                .region(Region.of(properties.region()));

        if (properties.accessKey() != null && !properties.accessKey().isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())));
        }

        if (properties.endpoint() != null && !properties.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }

        try (var client = builder.build()) {
            String json = client.getSecretValue(
                            GetSecretValueRequest.builder()
                                    .secretId(properties.secretName())
                                    .build())
                    .join()
                    .secretString();
            return new ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to retrieve database credentials from Secrets Manager: " + properties.secretName(), e);
        }
    }
}
