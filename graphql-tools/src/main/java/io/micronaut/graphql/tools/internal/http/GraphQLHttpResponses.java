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
package io.micronaut.graphql.tools.internal.http;

import io.micronaut.configuration.graphql.GraphQLResponseBody;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;

import java.util.List;

/**
 * The media type and the status of a GraphQL response over HTTP. A request names the types it accepts in {@code Accept}; the
 * endpoint answers {@code application/graphql-response+json} or {@code application/json}, and {@code application/json} when the
 * header is absent or accepts everything equally.
 *
 * <p>Under {@code application/graphql-response+json} a response without {@code data} (the request failed before the execution
 * started) has status {@code 400}, and one with {@code data}, also {@code null}, has status {@code 200}. Under
 * {@code application/json} the status is never changed. A status that an execution input customizer already set on the
 * response is kept.</p>
 *
 * <p>The parse of one {@code Accept} value reads it in place and allocates nothing. A request with several {@code Accept}
 * lines has them joined into one list first, which is rare.</p>
 */
@Internal
public final class GraphQLHttpResponses {

    /** The media type of the GraphQL response of the GraphQL over HTTP specification. */
    public static final String GRAPHQL_RESPONSE_JSON = "application/graphql-response+json";

    /** {@link #GRAPHQL_RESPONSE_JSON} as a media type. */
    public static final MediaType GRAPHQL_RESPONSE_JSON_TYPE = new MediaType(GRAPHQL_RESPONSE_JSON);

    private static final String JSON = MediaType.APPLICATION_JSON;
    private static final int SPECIFICITY_ANY = 0;
    private static final int SPECIFICITY_TYPE = 1;
    private static final int SPECIFICITY_EXACT = 2;
    private static final int FULL_Q = 1000;

    private GraphQLHttpResponses() {
    }

    /**
     * @param request the request
     * @return the media type of the response, or {@code null} when {@code Accept} refuses both
     */
    @Nullable
    public static MediaType negotiate(HttpRequest<?> request) {
        List<String> fields = request.getHeaders().getAll(HttpHeaders.ACCEPT);
        return negotiate(fields.size() < 2 ? (fields.isEmpty() ? null : fields.get(0)) : String.join(",", fields));
    }

    /**
     * Picks the response type: the higher quality value wins; at an equal quality the more specific range, then the one listed
     * first, then {@code application/json}.
     *
     * @param accept the value of {@code Accept}, or {@code null}
     * @return the media type of the response, or {@code null} when {@code Accept} refuses both
     */
    @Nullable
    public static MediaType negotiate(@Nullable String accept) {
        if (accept == null || accept.isEmpty()) {
            return MediaType.APPLICATION_JSON_TYPE;
        }
        int jsonQ = -1;
        int jsonSpecificity = -1;
        int jsonPosition = 0;
        int graphQLQ = -1;
        int graphQLSpecificity = -1;
        int graphQLPosition = 0;
        int length = accept.length();
        int position = 0;
        int start = 0;
        while (start <= length) {
            int end = indexOf(accept, ',', start, length);
            int typeEnd = indexOf(accept, ';', start, end);
            int from = skipSpaces(accept, start, typeEnd);
            int to = trimEnd(accept, from, typeEnd);
            if (to > from) {
                int q = typeEnd < end ? quality(accept, typeEnd + 1, end) : FULL_Q;
                int json = specificity(accept, from, to, JSON);
                if (json > jsonSpecificity || json == jsonSpecificity && json >= 0 && q > jsonQ) {
                    jsonSpecificity = json;
                    jsonQ = q;
                    jsonPosition = position;
                }
                int graphQL = specificity(accept, from, to, GRAPHQL_RESPONSE_JSON);
                if (graphQL > graphQLSpecificity || graphQL == graphQLSpecificity && graphQL >= 0 && q > graphQLQ) {
                    graphQLSpecificity = graphQL;
                    graphQLQ = q;
                    graphQLPosition = position;
                }
                position++;
            }
            start = end + 1;
        }
        boolean jsonAcceptable = jsonQ > 0;
        boolean graphQLAcceptable = graphQLQ > 0;
        if (!jsonAcceptable && !graphQLAcceptable) {
            return position == 0 ? MediaType.APPLICATION_JSON_TYPE : null;
        }
        if (!graphQLAcceptable) {
            return MediaType.APPLICATION_JSON_TYPE;
        }
        if (!jsonAcceptable || graphQLQ > jsonQ) {
            return GRAPHQL_RESPONSE_JSON_TYPE;
        }
        if (graphQLQ < jsonQ || graphQLSpecificity < jsonSpecificity) {
            return MediaType.APPLICATION_JSON_TYPE;
        }
        if (graphQLSpecificity > jsonSpecificity || graphQLPosition < jsonPosition) {
            return GRAPHQL_RESPONSE_JSON_TYPE;
        }
        return MediaType.APPLICATION_JSON_TYPE;
    }

    /**
     * Sets the media type and, under {@code application/graphql-response+json}, the status of a response whose body is the
     * given GraphQL response.
     *
     * @param response the response, whose status is {@code 200} unless a customizer changed it
     * @param type     the negotiated media type
     * @param body     the GraphQL response
     * @param <T>      the type of the body
     * @return the response
     */
    public static <T> MutableHttpResponse<T> prepare(MutableHttpResponse<T> response, MediaType type, GraphQLResponseBody body) {
        return prepare(response, type, body.getSpecification().containsKey("data"));
    }

    /**
     * @param response    the response, whose status is {@code 200} unless a customizer changed it
     * @param type        the negotiated media type
     * @param dataPresent whether the GraphQL response has a {@code data} entry
     * @param <T>         the type of the body
     * @return the response
     */
    public static <T> MutableHttpResponse<T> prepare(MutableHttpResponse<T> response, MediaType type, boolean dataPresent) {
        response.contentType(type).header(HttpHeaders.VARY, HttpHeaders.ACCEPT);
        if (!dataPresent && type == GRAPHQL_RESPONSE_JSON_TYPE && response.status() == HttpStatus.OK) {
            response.status(HttpStatus.BAD_REQUEST);
        }
        return response;
    }

    /**
     * @return the response of a request whose {@code Accept} lists neither media type
     */
    public static MutableHttpResponse<String> notAcceptable() {
        return HttpResponse.<String>status(HttpStatus.NOT_ACCEPTABLE).header(HttpHeaders.VARY, HttpHeaders.ACCEPT);
    }

    /**
     * @param s     the header value
     * @param ch    the delimiter
     * @param from  where to start
     * @param to    where to stop
     * @return the index of the first {@code ch} in {@code [from, to)} outside a quoted string, or {@code to}
     */
    private static int indexOf(String s, char ch, int from, int to) {
        boolean quoted = false;
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            if (quoted) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    quoted = false;
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ch) {
                return i;
            }
        }
        return to;
    }

    private static int specificity(String accept, int from, int to, String type) {
        int length = to - from;
        if (length == 3 && accept.charAt(from) == '*' && accept.charAt(from + 1) == '/' && accept.charAt(from + 2) == '*') {
            return SPECIFICITY_ANY;
        }
        if (length == type.length() && accept.regionMatches(true, from, type, 0, length)) {
            return SPECIFICITY_EXACT;
        }
        if (length == 13 && accept.regionMatches(true, from, "application/*", 0, 13)) {
            return SPECIFICITY_TYPE;
        }
        return -1;
    }

    private static int quality(String accept, int from, int to) {
        int i = from;
        while (i < to) {
            i = skipSpaces(accept, i, to);
            int paramEnd = indexOf(accept, ';', i, to);
            if (paramEnd - i >= 2 && (accept.charAt(i) == 'q' || accept.charAt(i) == 'Q') && accept.charAt(i + 1) == '=') {
                return parseQuality(accept, i + 2, trimEnd(accept, i + 2, paramEnd));
            }
            i = paramEnd + 1;
        }
        return FULL_Q;
    }

    private static int parseQuality(String accept, int from, int to) {
        int value = 0;
        int scale = FULL_Q;
        boolean fraction = false;
        for (int i = from; i < to; i++) {
            char c = accept.charAt(i);
            if (c == '.' && !fraction) {
                fraction = true;
            } else if (c >= '0' && c <= '9') {
                if (fraction) {
                    scale /= 10;
                    value += (c - '0') * scale;
                } else {
                    value = (c - '0') * FULL_Q;
                }
            } else {
                return FULL_Q;
            }
        }
        return Math.min(value, FULL_Q);
    }

    private static int skipSpaces(String s, int from, int to) {
        int i = from;
        while (i < to && s.charAt(i) <= ' ') {
            i++;
        }
        return i;
    }

    private static int trimEnd(String s, int from, int to) {
        int i = to;
        while (i > from && s.charAt(i - 1) <= ' ') {
            i--;
        }
        return i;
    }

}
