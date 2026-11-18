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
package example.tck

import graphql.GraphqlErrorBuilder
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType

import java.util.OptionalDouble
import java.util.OptionalInt
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

// The fixtures of the TCK scenario `return-types`.

@CompileStatic
@GraphQLType
class Greeting {
    final String message

    Greeting(String message) {
        this.message = message
    }
}

@CompileStatic
@GraphQLType
class Scalars {
    int getCount() {
        return Integer.MAX_VALUE
    }

    double getRatio() {
        return 0.5d
    }

    boolean isFlag() {
        return true
    }

    String getId() {
        return '9007199254740993'
    }

    Double getBig() {
        return null
    }
}

@CompileStatic
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'return-types')
class ReturnTypesQueries {

    Greeting plain() {
        return new Greeting('hello')
    }

    Optional<Greeting> present() {
        return Optional.of(new Greeting('present'))
    }

    Optional<Greeting> absent() {
        return Optional.empty()
    }

    CompletionStage<Greeting> future(String name) {
        return CompletableFuture.supplyAsync { new Greeting("hello $name".toString()) }
    }

    CompletionStage<List<Greeting>> futureList() {
        return CompletableFuture.supplyAsync { [new Greeting('one'), new Greeting('two')] as List<Greeting> }
    }

    CompletionStage<Optional<Greeting>> futureAbsent() {
        return CompletableFuture.completedFuture(Optional.<Greeting> empty())
    }

    DataFetcherResult<Greeting> result() {
        return DataFetcherResult.<Greeting> newResult().data(new Greeting('from a data fetcher result')).build()
    }

    DataFetcherResult<List<Greeting>> partial(DataFetchingEnvironment environment) {
        return DataFetcherResult.<List<Greeting>> newResult()
                .data([new Greeting('loaded'), null] as List<Greeting>)
                .error(GraphqlErrorBuilder.newError(environment).message('the second element could not be loaded').build())
                .build()
    }

    List<Integer> nulls() {
        return [1, null, 3] as List<Integer>
    }

    String nothing() {
        return null
    }

    Scalars scalars() {
        return new Scalars()
    }

    OptionalInt primitiveCount() {
        return OptionalInt.of(7)
    }

    OptionalInt primitiveAbsentCount() {
        return OptionalInt.empty()
    }

    OptionalDouble primitiveRatio() {
        return OptionalDouble.of(0.5d)
    }

    List<OptionalInt> primitiveCounts() {
        return [OptionalInt.of(1), OptionalInt.empty(), OptionalInt.of(3)]
    }
}
