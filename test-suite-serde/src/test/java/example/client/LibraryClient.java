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

import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.client.GraphQLResponse;
import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;
import io.micronaut.graphql.tools.client.annotation.GraphQLVariable;
import io.micronaut.http.annotation.Header;
import io.micronaut.serde.annotation.Serdeable;
import org.reactivestreams.Publisher;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A declarative client of the library server of this suite. The annotation processor checks each operation against the schema of
 * the server and compiles it, so these methods run what it wrote.
 */
@GraphQLClient(id = "library", schema = "server")
public interface LibraryClient {

    @Serdeable
    record Person(String username, String fullName) {
    }

    @Serdeable
    record Name(String username) {
    }

    @Serdeable
    record SignIn(Name data, Map<String, Object> error) {
    }

    String FIND = "query Find($contains: String!) { searchProfile(contains: $contains) { username fullName } }";

    @GraphQLOperation(FIND)
    List<Person> find(String contains);

    @GraphQLOperation(FIND)
    CompletableFuture<List<Person>> findFuture(String contains);

    @GraphQLOperation(FIND)
    Publisher<List<Person>> findPublisher(String contains);

    @GraphQLOperation(FIND)
    GraphQLResponse<List<Person>> findResponse(String contains);

    @GraphQLOperation("FindNames")
    List<Name> findNames(String contains);

    @GraphQLOperation(value = "query Limited($contains: String!, $limit: Int) { searchProfile(contains: $contains, limit: $limit) { username } }",
            method = GraphQLOperation.Method.GET)
    List<Name> findGet(String contains, @Nullable Integer limit);

    @GraphQLOperation("query Limited($contains: String!, $limit: Int) { searchProfile(contains: $contains, limit: $limit) { username } }")
    List<Name> findLimited(String contains, @Nullable Integer limit);

    @GraphQLOperation("query Limited($contains: String!, $limit: Int) { searchProfile(contains: $contains, limit: $limit) { username } }")
    GraphQLResponse<List<Name>> findLimitedResponse(String contains, @Nullable Integer limit);

    @GraphQLOperation("mutation SignIn($username: String!, $password: String!) { signIn(username: $username, password: $password) { data { username } error { __typename ... on ValidationError { message } } } }")
    SignIn signIn(@GraphQLVariable("username") String user, String password);

    @GraphQLOperation("query Current { currentUser { username } }")
    @Nullable
    Name currentUser();

    @GraphQLOperation("query Current { currentUser { username } }")
    void ping();

    @Header(name = "X-Declared", value = "type-level-style")
    @GraphQLOperation("query Current { currentUser { username } }")
    @Nullable
    Name tagged(@Header("X-Trace") String trace);
}
