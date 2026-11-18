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
package example.scope;

import graphql.schema.GraphQLSchema;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

@MicronautTest(environments = ScopedResolvers.ENVIRONMENT)
class ResolverScopesTest {

    @Inject
    EmbeddedServer server;

    @Inject
    ApplicationContext context;

    @Inject
    GraphQLSchema schema;

    @Test
    void aSingletonResolverIsOneInstanceAndAPrototypeResolverIsANewInstancePerCall() throws Exception {
        int singleton = id("singletonId");
        int prototype = id("prototypeId");

        assertEquals(singleton, id("singletonId"));
        assertNotEquals(prototype, id("prototypeId"));
    }

    @Test
    void aRequestScopedResolverIsOneInstancePerHttpRequest() throws Exception {
        String twoCalls = post("{ a: requestId b: requestId }");

        assertEquals(value(twoCalls, "a"), value(twoCalls, "b"));
        assertNotEquals(id("requestId"), id("requestId"));
    }

    @Test
    void aSecondSchemaBuildBindsTheSameFieldsAsTheFirst() {
        GraphQLSchema second = context.createBean(GraphQLSchema.class);

        assertNotSame(schema, second);
        assertNotNull(second.getQueryType().getFieldDefinition("prototypeId"));
        assertEquals(schema.getQueryType().getFieldDefinitions().size(), second.getQueryType().getFieldDefinitions().size());
    }

    private int id(String field) throws IOException, InterruptedException {
        return value(post("{ " + field + " }"), field);
    }

    private String post(String query) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + server.getHost() + ":" + server.getPort() + "/graphql"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"" + query + "\"}"))
                .build();
        String body = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
        assertEquals(false, body.contains("errors"), body);
        return body;
    }

    private static int value(String body, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":(\\d+)").matcher(body);
        assertEquals(true, matcher.find(), body);
        return Integer.parseInt(matcher.group(1));
    }

}
