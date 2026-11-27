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

import io.micronaut.graphql.tools.client.GraphQLHttpClient;
import io.micronaut.graphql.tools.client.GraphQLRequest;
import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.exceptions.GraphQLClientException;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

@Controller("/client-probe")
public class BlockingOnEventLoopController {

    private final GraphQLHttpClient client;

    public BlockingOnEventLoopController(@GraphQLClient("library") GraphQLHttpClient client) {
        this.client = client;
    }

    @Get("/event-loop")
    public Publisher<String> onTheEventLoop() {
        try {
            client.executeBlocking(GraphQLRequest.builder().document("{ currentUser { username } }").build());
            return Mono.just("completed");
        } catch (GraphQLClientException e) {
            return Mono.just(e.getCode());
        }
    }

    @ExecuteOn(TaskExecutors.BLOCKING)
    @Get("/worker")
    public String onAWorker() {
        return String.valueOf(client.executeBlocking(GraphQLRequest.builder().document("{ currentUser { username } }").build()).dataOrThrow());
    }
}
