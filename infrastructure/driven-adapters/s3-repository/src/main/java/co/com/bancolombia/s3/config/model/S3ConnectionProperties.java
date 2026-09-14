package co.com.bancolombia.s3.config.model;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "adapters.aws.s3")
public record S3ConnectionProperties(
        String endpoint,
        String region,
        String bucketName,
        String accessKey,
        String secretKey,
        @DefaultValue("results") String resultPrefix) {
}
