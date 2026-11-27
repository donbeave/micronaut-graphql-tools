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
package example.client;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;

import java.util.concurrent.atomic.AtomicReference;

@ServerFilter("/graphql")
public class SessionCookieFilter {

    static final AtomicReference<String> SEEN = new AtomicReference<>();

    @RequestFilter
    public void record(HttpRequest<?> request) {
        SEEN.set(request.getHeaders().get("Cookie"));
    }

    @ResponseFilter
    public void set(HttpRequest<?> request, MutableHttpResponse<?> response) {
        request.getHeaders().get("X-Set-Cookie", String.class).ifPresent(cookie -> response.getHeaders().add("Set-Cookie", cookie));
    }
}
