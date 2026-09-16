package co.com.bancolombia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "adapters.aws.secrets")
public record DatabaseSecretProperties(
        String secretName,
        String endpoint,
        String region,
        String accessKey,
        String secretKey) {
}
