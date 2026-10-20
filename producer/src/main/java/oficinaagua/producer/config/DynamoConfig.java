package oficinaagua.producer.config;

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
     * Cliente de DynamoDB del producer (HU-46): solo consulta el historial de envios.
     * endpointOverride lo apunta a DynamoDB Local en vez de a AWS.
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