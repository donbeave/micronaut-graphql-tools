package example;

import com.apollographql.apollo.ApolloClient;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class AbstractTest {

    @Inject
    EmbeddedServer embeddedServer;

    protected ApolloClient apolloClient;

    @BeforeEach
    public void setup() {
        String url = "http://" + embeddedServer.getHost() + ":" + embeddedServer.getPort() + "/graphql";

        apolloClient = new ApolloClient.Builder()
                .serverUrl(url)
                .build();
    }

    @AfterEach
    public void cleanup() {
        apolloClient.close();
    }

}
