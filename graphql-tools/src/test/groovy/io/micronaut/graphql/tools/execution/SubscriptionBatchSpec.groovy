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
package io.micronaut.graphql.tools.execution

import graphql.ExecutionResult
import graphql.schema.DataFetchingEnvironment
import io.micronaut.configuration.graphql.GraphQLInvocation
import io.micronaut.configuration.graphql.GraphQLInvocationData
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractSpec
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import jakarta.inject.Singleton
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.intellij.lang.annotations.Language
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/** The batch sizes that the batch function of a loader sees in one subscription event and in one query. */
class SubscriptionBatchSpec extends AbstractSpec {

    static final String SPEC = 'SubscriptionBatchSpec'

    static final List<List<String>> BATCHES = Collections.synchronizedList([])

    @Language('GraphQL')
    static final String SCHEMA = """
schema {
  query: Query
  subscription: Subscription
}

type Query {
  authors(count: Int!): [Author!]!
}

type Subscription {
  authorStream(count: Int!, events: Int!): [Author!]!
  feed(events: Int!): Feed!
}

type Feed {
  names(count: Int!): [String!]!
}

type Author {
  id: ID!
  name: String
}
"""

    void setup() {
        BATCHES.clear()
    }

    private ExecutionResult run(String query) {
        Mono.from(applicationContext.getBean(GraphQLInvocation).invoke(new GraphQLInvocationData(query, null, [:]), null, null)).block()
    }

    void "a query dispatches the loads of one level as one batch"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        def result = run('{ authors(count: 3) { name } }')

        then:
        result.errors.isEmpty()
        result.data.authors*.name == ['n0', 'n1', 'n2']
        BATCHES*.size() == [3]
    }

    void "the loads of separate fields in one subscription event are batched where graphql-java dispatches per level, and dispatched one by one where it dispatches after each fetch"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        def result = run('subscription { authorStream(count: 3, events: 2) { name } }')
        def events = Flux.from(result.<Publisher<ExecutionResult>> getData()).collectList().block()

        then:
        events.size() == 2
        events.every { it.errors.isEmpty() }
        events.every { it.data.authorStream*.name == ['n0', 'n1', 'n2'] }
        BATCHES*.size() == (dispatchesAfterEachFetch() ? [1, 1, 1, 1, 1, 1] : [3, 3])
    }

    void "loadMany in the fetcher of the list field reaches the batch function as one batch per event on every graphql-java line"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        def result = run('subscription { feed(events: 2) { names(count: 3) } }')
        def events = Flux.from(result.<Publisher<ExecutionResult>> getData()).collectList().block()

        then:
        events.size() == 2
        events.every { it.errors.isEmpty() }
        events.every { it.data.feed.names == ['n0', 'n1', 'n2'] }
        BATCHES*.size() == [3, 3]
    }

    private static boolean dispatchesAfterEachFetch() {
        ['graphql.execution.instrumentation.dataloader.FallbackDataLoaderDispatchStrategy',
         'graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation'].any {
            try {
                Class.forName(it)
                true
            } catch (ClassNotFoundException ignored) {
                false
            }
        }
    }

    @Requires(property = 'spec.name', value = SPEC)
    @Singleton
    static class NameLoader implements BatchLoader<String, String> {
        @Override
        CompletionStage<List<String>> load(List<String> keys) {
            BATCHES << new ArrayList<>(keys)
            CompletableFuture.completedFuture(keys.collect { 'n' + it })
        }
    }

    @Requires(property = 'spec.name', value = SPEC)
    @Singleton
    @GraphQLRootResolver
    static class Query {
        List<Author> authors(int count) {
            (0..<count).collect { new Author(id: it.toString()) }
        }

        Publisher<Feed> feed(int events) {
            Flux.range(0, events).map { new Feed() }
        }

        Publisher<List<Author>> authorStream(int count, int events) {
            Flux.range(0, events).map { (0..<count).collect { new Author(id: it.toString()) } }
        }
    }

    @GraphQLType
    static class Feed {
    }

    @GraphQLType
    static class Author {
        String id
    }

    @Requires(property = 'spec.name', value = SPEC)
    @Singleton
    @GraphQLTypeResolver(Author)
    static class AuthorResolver {
        CompletionStage<String> name(Author author, DataFetchingEnvironment environment) {
            environment.<DataLoader<String, String>> getDataLoader('nameLoader').load(author.id)
        }
    }

    @Requires(property = 'spec.name', value = SPEC)
    @Singleton
    @GraphQLTypeResolver(Feed)
    static class FeedResolver {
        CompletionStage<List<String>> names(Feed feed, int count, DataFetchingEnvironment environment) {
            environment.<DataLoader<String, String>> getDataLoader('nameLoader').loadMany((0..<count).collect { it.toString() })
        }
    }
}
