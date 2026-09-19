package co.com.bancolombia.dynamodb.config.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "adapters.aws.dynamodb")
public record DynamoDBConnectionProperties(
        String endpoint,
        String region,
        String accessKey,
        String secretKey) {
}
