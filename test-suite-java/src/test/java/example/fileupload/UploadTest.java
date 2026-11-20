/*
 * Copyright 2021-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package example.fileupload;

import io.micronaut.context.ApplicationContext;
import io.micronaut.graphql.tools.upload.GraphQLUpload;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.json.JsonMapper;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Runs on the JVM and as a native binary (nativeTest): a real multipart request, from the client to the resolver.
class UploadTest {

    private static final Map<String, Object> SERVER = Map.of(
            "micronaut.server.host", "127.0.0.1",
            "micronaut.server.port", -1,
            "graphql.graphiql.enabled", false);

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, Object> post(ApplicationContext context, EmbeddedServer server, MultipartBody body) throws IOException {
        try (HttpClient client = context.createBean(HttpClient.class, server.getURL())) {
            String response = client.toBlocking()
                    .retrieve(HttpRequest.POST("/graphql", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE), String.class);
            return context.getBean(JsonMapper.class).readValue(response, Map.class);
        }
    }

    // tag::top-level[]
    @Test
    void aMutationReceivesTheUploadedFile() throws IOException {
        try (ApplicationContext context = ApplicationContext.run(EmbeddedServer.class, SERVER, UploadFixtures.UPLOAD).getApplicationContext()) {
            EmbeddedServer server = context.getBean(EmbeddedServer.class);
            MultipartBody body = MultipartBody.builder()
                    .addPart("operations", "{\"query\":\"mutation ($file: Upload!) { uploadedFile(file: $file) }\","
                            + "\"variables\":{\"file\":null}}")
                    .addPart("map", "{\"0\":[\"variables.file\"]}")
                    .addPart("0", "notes.txt", MediaType.TEXT_PLAIN_TYPE, bytes("hello"))
                    .build();

            Map<String, Object> response = post(context, server, body);

            assertNull(response.get("errors"));
            assertEquals(Map.of("uploadedFile", "notes.txt:hello"), response.get("data"));
        }
    }
    // end::top-level[]

    // tag::input-object[]
    @Test
    void aMutationReceivesFilesInsideAnInputObject() throws IOException {
        try (ApplicationContext context = ApplicationContext.run(EmbeddedServer.class, SERVER, UploadFixtures.UPLOAD).getApplicationContext()) {
            EmbeddedServer server = context.getBean(EmbeddedServer.class);
            MultipartBody body = MultipartBody.builder()
                    .addPart("operations", "{\"query\":\"mutation ($input: BookUpload!) { uploadedBook(input: $input) }\","
                            + "\"variables\":{\"input\":{\"title\":\"Dune\",\"cover\":null,\"pages\":[null,null]}}}")
                    .addPart("map", "{\"0\":[\"variables.input.cover\"],\"1\":[\"variables.input.pages.0\"],"
                            + "\"2\":[\"variables.input.pages.1\"]}")
                    .addPart("0", "cover.png", MediaType.IMAGE_PNG_TYPE, bytes("cover"))
                    .addPart("1", "1.txt", MediaType.TEXT_PLAIN_TYPE, bytes("one"))
                    .addPart("2", "2.txt", MediaType.TEXT_PLAIN_TYPE, bytes("two"))
                    .build();

            Map<String, Object> response = post(context, server, body);

            assertNull(response.get("errors"));
            assertEquals(Map.of("uploadedBook", "Dune cover=cover page=one page=two"), response.get("data"));
        }
    }
    // end::input-object[]

    // tag::executor[]
    @Test
    void theResolverRunsOffTheEventLoop() throws IOException {
        try (ApplicationContext context = ApplicationContext.run(EmbeddedServer.class, SERVER, UploadFixtures.UPLOAD).getApplicationContext()) {
            EmbeddedServer server = context.getBean(EmbeddedServer.class);
            MultipartBody body = MultipartBody.builder()
                    .addPart("operations", "{\"query\":\"mutation ($file: Upload!) { uploadedOn(file: $file) }\","
                            + "\"variables\":{\"file\":null}}")
                    .addPart("map", "{\"0\":[\"variables.file\"]}")
                    .addPart("0", "a.txt", MediaType.TEXT_PLAIN_TYPE, bytes("x"))
                    .build();

            Map<?, ?> data = (Map<?, ?>) post(context, server, body).get("data");

            assertFalse(String.valueOf(data.get("uploadedOn")).contains("event-loop"), String.valueOf(data));
            assertFalse(String.valueOf(data.get("uploadedOn")).contains("nioEventLoopGroup"), String.valueOf(data));
        }
    }
    // end::executor[]

    // tag::invalid[]
    @Test
    void aMapThatNamesAMissingPartIsAnErrorWithACode() throws IOException {
        try (ApplicationContext context = ApplicationContext.run(EmbeddedServer.class, SERVER, UploadFixtures.UPLOAD).getApplicationContext()) {
            EmbeddedServer server = context.getBean(EmbeddedServer.class);
            MultipartBody body = MultipartBody.builder()
                    .addPart("operations", "{\"query\":\"mutation ($file: Upload!) { uploadedFile(file: $file) }\","
                            + "\"variables\":{\"file\":null}}")
                    .addPart("map", "{\"0\":[\"variables.file\"]}")
                    .build();

            HttpClientResponseException failure = assertThrows(HttpClientResponseException.class, () -> post(context, server, body));

            assertEquals(HttpStatus.BAD_REQUEST, failure.getStatus());
            Map<?, ?> error = (Map<?, ?>) ((List<?>) context.getBean(JsonMapper.class)
                    .readValue(failure.getResponse().getBody(String.class).orElseThrow(), Map.class).get("errors")).get(0);
            assertEquals(GraphQLUpload.INVALID_REQUEST_CODE, ((Map<?, ?>) error.get("extensions")).get("code"));
            assertTrue(String.valueOf(error.get("message")).contains("no such part"));
        }
    }
    // end::invalid[]

    // tag::precedence[]
    @Test
    void aJsonRequestOnTheSamePathStillReachesTheTransportRoute() throws IOException {
        try (ApplicationContext context = ApplicationContext.run(EmbeddedServer.class, SERVER, UploadFixtures.UPLOAD).getApplicationContext()) {
            EmbeddedServer server = context.getBean(EmbeddedServer.class);
            try (HttpClient client = context.createBean(HttpClient.class, server.getURL())) {
                String response = client.toBlocking().retrieve(HttpRequest.POST("/graphql", "{\"query\":\"{ __typename }\"}")
                        .contentType(MediaType.APPLICATION_JSON_TYPE), String.class);

                assertEquals(Map.of("data", Map.of("__typename", "Query")), context.getBean(JsonMapper.class).readValue(response, Map.class));
            }
        }
    }
    // end::precedence[]

}
