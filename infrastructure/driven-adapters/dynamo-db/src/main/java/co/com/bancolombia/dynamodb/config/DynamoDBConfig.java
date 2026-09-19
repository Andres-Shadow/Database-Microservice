package co.com.bancolombia.dynamodb.config;

import co.com.bancolombia.dynamodb.config.model.DynamoDBConnectionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.metrics.MetricPublisher;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

import java.net.URI;

@Configuration
public class DynamoDBConfig {

    @Bean
    @Profile({"default", "local"})
    public DynamoDbAsyncClient amazonDynamoDB(DynamoDBConnectionProperties properties,
                                               MetricPublisher publisher) {
        return DynamoDbAsyncClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .region(Region.of(properties.region()))
                .endpointOverride(URI.create(properties.endpoint()))
                .overrideConfiguration(o -> o.addMetricPublisher(publisher))
                .build();
    }

    @Bean
    @Profile({"dev", "cer", "pdn"})
    public DynamoDbAsyncClient amazonDynamoDBAsync(DynamoDBConnectionProperties properties,
                                                    MetricPublisher publisher) {
        return DynamoDbAsyncClient.builder()
                .credentialsProvider(WebIdentityTokenFileCredentialsProvider.create())
                .region(Region.of(properties.region()))
                .overrideConfiguration(o -> o.addMetricPublisher(publisher))
                .build();
    }

    @Bean
    public DynamoDbEnhancedAsyncClient getDynamoDbEnhancedAsyncClient(DynamoDbAsyncClient client) {
        return DynamoDbEnhancedAsyncClient.builder()
                .dynamoDbClient(client)
                .build();
    }
}
