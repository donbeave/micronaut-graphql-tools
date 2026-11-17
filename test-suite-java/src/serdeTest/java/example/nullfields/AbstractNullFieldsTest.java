package example.nullfields;

import example.data.AuthenticationService;
import io.micronaut.json.JsonMapper;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A GraphQL response keeps the fields whose value is null: {@code {"data":{"currentUser":null}}}, not {@code {"data":{}}}.
 * A client reads a missing key and an explicit null differently, so the response must carry the key. The subclasses run
 * these checks under each JSON configuration of this stack, over raw HTTP (the typed Apollo client would hide a missing
 * key behind a null field).
 *
 * <p>Stack: Micronaut Serialization (micronaut-serde-jackson), the preferred stack (D-03).
 */
abstract class AbstractNullFieldsTest {

    @Inject
    EmbeddedServer embeddedServer;

    @Inject
    JsonMapper jsonMapper;

    @Inject
    AuthenticationService authenticationService;

    /** A successful signIn logs the user in for the whole application: a guest is what the null checks need. */
    @AfterEach
    void logout() {
        authenticationService.logout();
    }

    /** The {@link JsonMapper} implementation the configuration must select: a guard that the test runs the stack it names. */
    abstract String expectedMapperClassName();

    @Test
    void usesTheExpectedJsonStack() {
        assertEquals(expectedMapperClassName(), jsonMapper.getClass().getName());
    }

    @Test
    void nullObjectFieldKeepsItsKey() throws Exception {
        Map<String, Object> data = data(post("{ currentUser { username } }"));

        assertTrue(data.containsKey("currentUser"), "data: " + data);
        assertNull(data.get("currentUser"));
    }

    @Test
    void nullFieldsOfAPayloadKeepTheirKeys() throws Exception {
        Map<String, Object> data = data(post("mutation { signIn(username: \"unknown-user\", password: \"x\") "
                + "{ data { username } error { ... on ValidationError { message } } } }"));

        Map<?, ?> signIn = assertInstanceOf(Map.class, data.get("signIn"));
        assertTrue(signIn.containsKey("data"), "signIn: " + signIn);
        assertNull(signIn.get("data"));
        assertInstanceOf(Map.class, signIn.get("error"));

        data = data(post("mutation { signIn(username: \"test\", password: \"test\") "
                + "{ data { username } error { ... on ValidationError { message } } } }"));

        signIn = assertInstanceOf(Map.class, data.get("signIn"));
        assertTrue(signIn.containsKey("error"), "signIn: " + signIn);
        assertNull(signIn.get("error"));
        assertInstanceOf(Map.class, signIn.get("data"));
    }

    @Test
    void emptyListIsNotDropped() throws Exception {
        Map<String, Object> data = data(post("{ searchProfile(contains: \"Z\") { username } }"));

        assertEquals(List.of(), data.get("searchProfile"));
    }

    @Test
    void fetcherErrorKeepsTheDataKey() throws Exception {
        // a non-null field that fails: the null bubbles up to data, which is then null, and the key stays
        Map<String, Object> response = post("{ searchProfile(contains: \"t\", limit: -1) { username } }");

        assertTrue(response.containsKey("errors"), "response: " + response);
        assertTrue(response.containsKey("data"), "response: " + response);
        assertNull(response.get("data"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(Map<String, Object> response) {
        assertTrue(!response.containsKey("errors"), "response: " + response);
        return (Map<String, Object>) assertInstanceOf(Map.class, response.get("data"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String query) throws IOException, InterruptedException {
        String body = jsonMapper.writeValueAsString(Map.of("query", query));
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + embeddedServer.getHost() + ":"
                        + embeddedServer.getPort() + "/graphql"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return jsonMapper.readValue(response.body(), Map.class);
        }
    }

}
