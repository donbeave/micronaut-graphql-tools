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
package io.micronaut.graphql.tools.upload.internal;

import graphql.GraphqlErrorBuilder;
import io.micronaut.configuration.graphql.GraphQLExecutionResultHandler;
import io.micronaut.configuration.graphql.GraphQLInvocation;
import io.micronaut.configuration.graphql.GraphQLInvocationData;
import io.micronaut.configuration.graphql.GraphQLJsonSerializer;
import io.micronaut.configuration.graphql.RequiresGraphQL;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.internal.http.GraphQLHttpResponses;
import io.micronaut.graphql.tools.upload.GraphQLUpload;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.CompletedPart;
import io.micronaut.http.server.multipart.MultipartBody;
import io.micronaut.json.JsonMapper;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The route of the GraphQL multipart request specification: a {@code multipart/form-data} POST to the path of the GraphQL
 * endpoint. The transport's own route there accepts every content type and cannot read a multipart body; this one accepts
 * {@code multipart/form-data} only, so the router picks it for that content type.
 *
 * <p>The route carries no {@code @ExecuteOn}: it reads the parts on the thread that receives them and hands the operation to the
 * {@link GraphQLInvocation} bean, which hops to the executor of the library once. Each part is a {@code CompletedPart} that
 * Micronaut completes under {@code micronaut.server.multipart.*} (memory, disk or mixed, with the size limit), so the request is
 * not buffered beyond what the server configuration says. The files are discarded when the response is complete.</p>
 */
@Internal
@Controller("${graphql.path:/graphql}")
@RequiresGraphQL
@Requires(property = GraphQLUpload.PREFIX + ".enabled", notEquals = "false")
public final class GraphQLMultipartController {

    private static final Logger LOG = LoggerFactory.getLogger(GraphQLMultipartController.class);

    private final GraphQLInvocation invocation;
    private final GraphQLExecutionResultHandler resultHandler;
    private final GraphQLJsonSerializer serializer;
    private final MultipartRequestReader reader;

    GraphQLMultipartController(GraphQLInvocation invocation, GraphQLExecutionResultHandler resultHandler,
                               GraphQLJsonSerializer serializer, JsonMapper jsonMapper) {
        this.invocation = invocation;
        this.resultHandler = resultHandler;
        this.serializer = serializer;
        this.reader = new MultipartRequestReader(jsonMapper);
    }

    /**
     * @param body    the parts of the request, each complete when it is emitted
     * @param request the request
     * @return the response of the operation, or a {@code 400} when the request does not follow the specification
     */
    @Post(consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.ALL)
    public Publisher<MutableHttpResponse<String>> post(@Body MultipartBody body, HttpRequest<?> request) {
        MediaType type = GraphQLHttpResponses.negotiate(request);
        if (type == null) {
            return Mono.just(HttpResponse.status(HttpStatus.NOT_ACCEPTABLE));
        }
        return Mono.defer(() -> {
            List<CompletedPart> received = new ArrayList<>();
            return Flux.from(body).doOnNext(received::add)
                    .then(Mono.defer(() -> execute(received, request, type)))
                    .onErrorResume(InvalidMultipartRequestException.class, e -> Mono.just(invalid(e, type)))
                    .doFinally(signal -> discard(received));
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Mono<MutableHttpResponse<String>> execute(List<CompletedPart> parts, HttpRequest<?> request,
                                                       MediaType type) {
        Map<String, CompletedPart> byName = new LinkedHashMap<>();
        for (CompletedPart part : parts) {
            if (byName.putIfAbsent(part.getName(), part) != null) {
                throw new InvalidMultipartRequestException("The part '" + part.getName() + "' is sent more than once.");
            }
        }
        GraphQLInvocationData data = reader.read(byName);
        MutableHttpResponse<String> response = HttpResponse.status(HttpStatus.OK);
        return Mono.from(resultHandler.handleExecutionResult(invocation.invoke(data, (HttpRequest) request, response)))
                .map(result -> GraphQLHttpResponses.prepare(response, type, result).body(serializer.serialize(result.getSpecification())));
    }

    private MutableHttpResponse<String> invalid(InvalidMultipartRequestException e, MediaType type) {
        Map<String, Object> error = GraphqlErrorBuilder.newError().message(e.getMessage())
                .extensions(Map.of("code", GraphQLUpload.INVALID_REQUEST_CODE)).build().toSpecification();
        return HttpResponse.<String>status(HttpStatus.BAD_REQUEST).contentType(type).body(serializer.serialize(Map.of("errors", List.of(error))));
    }

    private static void discard(List<CompletedPart> parts) {
        for (CompletedPart part : parts) {
            if (part instanceof CompletedFileUpload file) {
                try {
                    file.discard();
                } catch (RuntimeException e) {
                    LOG.debug("The upload '{}' could not be discarded", file.getName(), e);
                }
            }
        }
    }

}
