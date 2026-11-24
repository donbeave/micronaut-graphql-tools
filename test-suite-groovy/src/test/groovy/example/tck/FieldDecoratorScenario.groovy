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

import graphql.schema.DataFetcher
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.execution.FieldDescriptor
import io.micronaut.graphql.tools.execution.GraphQLFieldFetcherDecorator
import jakarta.inject.Singleton

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

// The fixtures of the TCK scenario `field-decorator`: a decorator that only sees fields with arguments, and resolvers for
// fields with and without arguments and for a property.

@Requires(property = 'tck.scenario', value = 'field-decorator')
// tag::decorator[]
@CompileStatic
@Singleton
class MarkingDecorator implements GraphQLFieldFetcherDecorator {

    @Override
    DataFetcher<Object> decorate(FieldDescriptor field, DataFetcher<Object> fetcher) {
        return { environment ->
            Object value = fetcher.get(environment)
            value instanceof CompletionStage ? ((CompletionStage<Object>) value).thenApply { "decorated:$it" as Object } : "decorated:$value" as Object
        } as DataFetcher<Object>
    }
}
// end::decorator[]

@CompileStatic
@GraphQLType
class DecoratedBox {

    String getLabel() {
        return 'label'
    }
}

@CompileStatic
@Singleton
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'field-decorator')
class DecoratorQueries {

    String decoratedEcho(String text) {
        return text
    }

    CompletionStage<String> decoratedAsync(String text) {
        return CompletableFuture.completedFuture(text)
    }

    String undecoratedPlain() {
        return 'plain'
    }

    DecoratedBox decoratedBox() {
        return new DecoratedBox()
    }
}
