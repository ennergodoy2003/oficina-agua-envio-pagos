package oficinaagua.consumer.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class DynamoConfig {

    /**
     * Un solo cliente de DynamoDB para todo el consumer (HU-46).
     * endpointOverride lo apunta a DynamoDB Local en vez de a AWS.
     * Las credenciales "local" no son secretas: DynamoDB Local solo exige que existan.
     */
    @Bean
    public DynamoDbClient dynamoDbClient(@Value("${dynamodb.endpoint}") String endpoint,
                                         @Value("${dynamodb.region}") String region) {
        return DynamoDbClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("local", "local")))
                .build();
    }
}