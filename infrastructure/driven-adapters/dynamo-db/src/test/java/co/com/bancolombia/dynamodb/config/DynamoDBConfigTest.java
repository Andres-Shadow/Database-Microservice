package co.com.bancolombia.dynamodb.config;

import co.com.bancolombia.dynamodb.config.model.DynamoDBConnectionProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.metrics.MetricPublisher;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class DynamoDBConfigTest {

    @Mock
    private MetricPublisher publisher;

    @Mock
    private DynamoDbAsyncClient dynamoDbAsyncClient;

    private final DynamoDBConfig dynamoDBConfig = new DynamoDBConfig();

    @Test
    void testAmazonDynamoDB() {
        DynamoDBConnectionProperties properties = new DynamoDBConnectionProperties(
                "http://aws.dynamo.test", "us-east-1", "test", "test");

        DynamoDbAsyncClient result = dynamoDBConfig.amazonDynamoDB(properties, publisher);

        assertNotNull(result);
    }

    @Test
    void testAmazonDynamoDBAsync() {
        DynamoDBConnectionProperties properties = new DynamoDBConnectionProperties(
                "http://aws.dynamo.test", "us-east-1", "test", "test");

        DynamoDbAsyncClient result = dynamoDBConfig.amazonDynamoDBAsync(properties, publisher);

        assertNotNull(result);
    }

    @Test
    void testGetDynamoDbEnhancedAsyncClient() {
        DynamoDbEnhancedAsyncClient result = dynamoDBConfig.getDynamoDbEnhancedAsyncClient(dynamoDbAsyncClient);

        assertNotNull(result);
    }
}
