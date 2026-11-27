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
package io.micronaut.graphql.tools.client;

import io.micronaut.core.annotation.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * A {@link GraphQLHttpClient} with a cookie session. It stores the cookies that the responses of its calls set, sends them on its
 * next calls, and sends its fixed headers on every call. Start one with {@link GraphQLHttpClient#newSession()}.
 *
 * <p>The session belongs to the code that started it, and that code decides how long it lives. Two sessions of one client share
 * nothing, and the client itself never keeps a cookie. A cookie is sent to the client id of the session only: its {@code Domain} is
 * ignored and never rewritten. A cookie is matched by name and path, replaced by a response that sets the same name and path, removed
 * by one that expires it ({@code Max-Age} of zero or less, or an {@code Expires} in the past), and dropped once its expiry has
 * passed. A cookie is sent when its path is a prefix of the path of the endpoint, by the rules of RFC 6265.</p>
 *
 * <p>A session is thread safe. Calls that run at the same time each send the cookies that were stored when they started.
 * {@link #newSession()} on a session starts a new, independent session of the same client.</p>
 *
 * @since 1.0.0
 */
public interface GraphQLSession extends GraphQLHttpClient {

    /**
     * Returns the cookies that the session would send now, the expired ones left out.
     *
     * @return a snapshot of the cookies
     */
    List<Cookie> cookies();

    /**
     * Forgets every cookie of the session; the fixed headers stay.
     */
    void clearCookies();

    /**
     * A cookie that a session keeps.
     *
     * @param name    the name
     * @param value   the value
     * @param path    the path that a request path must be under, {@code /} when the response gave none
     * @param expires the instant after which the cookie is dropped, or {@code null} for a cookie that lasts as long as the session
     */
    record Cookie(String name, String value, String path, @Nullable Instant expires) {
    }
}
