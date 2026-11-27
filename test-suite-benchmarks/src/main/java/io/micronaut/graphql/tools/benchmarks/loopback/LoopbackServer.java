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
package io.micronaut.graphql.tools.benchmarks.loopback;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The server of the loopback cases: every POST gets the same body of exactly {@link #BODY_BYTES} bytes, a response with the data
 * of the {@code book} root field.
 */
@Requires(property = LoopbackCases.ENABLED, value = "true")
@Controller("/loopback")
public class LoopbackServer {

    public static final int BODY_BYTES = 1024;

    private static final String BODY = body();

    @Post("/graphql")
    @Produces(MediaType.APPLICATION_JSON)
    public String post() {
        return BODY;
    }

    private static String body() {
        String head = "{\"data\":{\"book\":{\"id\":\"b-1\",\"title\":\"Dune\",\"pages\":412,\"tags\":[";
        String tail = "]}}}";
        List<String> tags = new ArrayList<>();
        String tag;
        int size = head.length() + tail.length();
        for (int i = 0; ; i++) {
            tag = "\"tag-" + String.format("%03d", i) + "\"";
            int next = size + tag.length() + (tags.isEmpty() ? 0 : 1);
            if (next > BODY_BYTES - 20) {
                break;
            }
            tags.add(tag);
            size = next;
        }
        int padding = BODY_BYTES - size - 3;
        String pad = padding > 0 ? "\"" + "x".repeat(padding) + "\"" : "";
        String joined = String.join(",", tags) + (pad.isEmpty() ? "" : "," + pad);
        String result = head + joined + tail;
        if (result.getBytes(StandardCharsets.UTF_8).length != BODY_BYTES) {
            throw new IllegalStateException("The loopback body has " + result.getBytes(StandardCharsets.UTF_8).length + " bytes, not " + BODY_BYTES);
        }
        return result;
    }
}
