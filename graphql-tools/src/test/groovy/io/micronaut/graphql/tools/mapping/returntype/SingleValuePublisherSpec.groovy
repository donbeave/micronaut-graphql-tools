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
package io.micronaut.graphql.tools.mapping.returntype

import jakarta.inject.Singleton
import graphql.ExecutionResult
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.core.async.publisher.CompletableFuturePublisher
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.manifest.BindingManifest
import org.intellij.lang.annotations.Language
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux

import java.util.concurrent.CompletableFuture

/** The single-value types of the shared return rule: RxJava, CompletableFuturePublisher and a {@code @SingleResult} method. */
class SingleValuePublisherSpec extends AbstractTest {

    static final String SPEC_NAME = 'SingleValuePublisherSpec'

    @Language('GraphQL')
    static final String SCHEMA = """
type Query {
  rx3Single: String
  rx3Maybe: String
  rx3EmptyMaybe: String
  rx3Completable: String
  rx2Completable: String
  rx2Single: String
  rx2Maybe: String
  futurePublisher: String
  singleResultPublisher: String
  singleResultFlux: String
  rx3Failing: String
  manyStrings: [String!]!
}
"""

    void "the RxJava types, CompletableFuturePublisher and @SingleResult bind to their value on a field that is not a list"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        ExecutionResult result = executeQuery('{ rx3Single rx3Maybe rx3EmptyMaybe rx3Completable rx2Completable rx2Single rx2Maybe futurePublisher singleResultPublisher singleResultFlux manyStrings }')

        then:
        result.errors.isEmpty()
        result.data == [rx3Single: 'a', rx3Maybe: 'b', rx3EmptyMaybe: null, rx3Completable: null, rx2Completable: null, rx2Single: 'c', rx2Maybe: 'd', futurePublisher: 'e',
                        singleResultPublisher: 'f', singleResultFlux: 'g', manyStrings: ['x', 'y']]
    }

    void "an error of an RxJava type is an error of the field"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        ExecutionResult result = executeQuery('{ rx3Failing }')

        then:
        result.errors.size() == 1
        result.errors[0].message.contains('boom')
    }

    void "the manifest records the value type of a @SingleResult publisher, not a list"() {
        given:
        startContext(SCHEMA, SPEC_NAME)
        getGraphQLBean()

        when:
        def lines = applicationContext.getBean(BindingManifest).lines

        then:
        lines.find { it.contains('Query.singleResultFlux') }.contains('java.lang.String')
        !lines.find { it.contains('Query.singleResultFlux') }.contains('List')
        lines.find { it.contains('Query.rx3Maybe') }.contains('java.lang.String')
        lines.find { it.contains('Query.manyStrings') }.contains('List<java.lang.String>')
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Query {
        io.reactivex.rxjava3.core.Single<String> rx3Single() {
            io.reactivex.rxjava3.core.Single.just('a')
        }

        io.reactivex.rxjava3.core.Maybe<String> rx3Maybe() {
            io.reactivex.rxjava3.core.Maybe.just('b')
        }

        io.reactivex.rxjava3.core.Maybe<String> rx3EmptyMaybe() {
            io.reactivex.rxjava3.core.Maybe.empty()
        }

        io.reactivex.rxjava3.core.Completable rx3Completable() {
            io.reactivex.rxjava3.core.Completable.complete()
        }

        io.reactivex.Completable rx2Completable() {
            io.reactivex.Completable.complete()
        }

        io.reactivex.Single<String> rx2Single() {
            io.reactivex.Single.just('c')
        }

        io.reactivex.Maybe<String> rx2Maybe() {
            io.reactivex.Maybe.just('d')
        }

        CompletableFuturePublisher<String> futurePublisher() {
            new CompletableFuturePublisher<String>({ CompletableFuture.supplyAsync { 'e' } })
        }

        @SingleResult
        Publisher<String> singleResultPublisher() {
            Flux.just('f')
        }

        @SingleResult
        Flux<String> singleResultFlux() {
            Flux.just('g', 'ignored')
        }

        io.reactivex.rxjava3.core.Single<String> rx3Failing() {
            io.reactivex.rxjava3.core.Single.error(new IllegalStateException('boom'))
        }

        Flux<String> manyStrings() {
            Flux.just('x', 'y')
        }
    }
}
