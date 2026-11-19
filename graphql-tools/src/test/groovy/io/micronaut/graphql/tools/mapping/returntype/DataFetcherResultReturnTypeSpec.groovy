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

import graphql.GraphqlErrorBuilder
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import org.intellij.lang.annotations.Language

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.binding.BindingCode

class DataFetcherResultReturnTypeSpec extends AbstractTest {

    static final String SPEC_NAME = "DataFetcherResultReturnTypeSpec"
    static final String SPEC_NAME_NESTED = "DataFetcherResultReturnTypeSpecNested"
    static final String SPEC_NAME_LIST_FUTURE = "DataFetcherResultReturnTypeSpecListFuture"
    static final String SPEC_NAME_RAW = "DataFetcherResultReturnTypeSpecRaw"
    static final String SPEC_NAME_INPUT = "DataFetcherResultReturnTypeSpecInput"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  item: Item
  asyncItem: Item!
  items: [Item!]!
  greeting: String
}

type Item {
  name: String!
  label: String!
}
"""

    void "DataFetcherResult at field level, inside CompletionStage and per list element; localContext reaches the child"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    item { name label }
    asyncItem { name label }
    items { name label }
}
""")

        then:
        result.errors.isEmpty()
        result.data.item == [name: 'first', label: 'first@context-field']
        result.data.asyncItem == [name: 'async', label: 'async@context-async']
        result.data.items == [
                [name: 'a', label: 'a@context-a'],
                [name: 'b', label: 'b@context-b']
        ]
    }

    void "errors of a DataFetcherResult are reported next to its data"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery("{ greeting }")

        then:
        result.data.greeting == 'hello'
        result.errors*.message == ['partial failure']
    }

    void "a DataFetcherResult nested in a DataFetcherResult fails at startup"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  greeting: String
}
"""
        startContext(schema, SPEC_NAME_NESTED)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.UNSUPPORTED_RETURN_TYPE
        e.cause.message == """[unsupported-return-type] The return type of the method or property is not supported for this field: ${DataFetcherResult.name} cannot wrap ${DataFetcherResult.name}. Supported wrappers, outermost first: CompletionStage (field level only), Publisher (subscription fields only), DataFetcherResult, Optional (nullable types only), each at most once. Fix: remove the inner ${DataFetcherResult.simpleName} or declare the wrappers in that order.
  GraphQL coordinate: Query.greeting (line 7, column 3)
  Mapped class: ${NestedQuery.name}
  Mapped method: greeting()"""
        e.cause.mappingContext.graphQlObjectType == 'Query'
        e.cause.mappingContext.graphQlField == 'greeting'
    }

    void "a CompletionStage inside a list fails at startup (graphql-java does not await list elements)"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  names: [String]
}
"""
        startContext(schema, SPEC_NAME_LIST_FUTURE)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.UNSUPPORTED_RETURN_TYPE
        e.cause.message.startsWith("[unsupported-return-type] The return type of the method or property is not supported for this field: " +
                "${CompletionStage.name} is not allowed inside a list. Fix: return CompletionStage<List<T>> instead " +
                "(for example, combine the element futures with CompletableFuture.allOf), or load the elements " +
                "with a DataLoader.")
        e.cause.message.contains("GraphQL coordinate: Query.names")
    }

    void "a raw DataFetcherResult fails at startup with the dedicated message"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  greeting: String
}
"""
        startContext(schema, SPEC_NAME_RAW)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.UNSUPPORTED_RETURN_TYPE
        e.cause.message.startsWith("[unsupported-return-type] The return type of the method or property is not supported for this field: " +
                "${DataFetcherResult.name} must declare the type it wraps.")
        e.cause.mappingContext.graphQlField == 'greeting'
    }

    void "a DataFetcherResult is not unwrapped for an input argument"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  search(filter: FilterInput): String
}

input FilterInput {
  term: String
}
"""
        startContext(schema, SPEC_NAME_INPUT)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.CLASS_NOT_INTROSPECTED
        e.cause.message.startsWith("[class-not-introspected] The class ${DataFetcherResult.name} is not introspected.")
        e.cause.message.contains("Reached from: Query.search(filter:)")
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        DataFetcherResult<Item> item() {
            return DataFetcherResult.<Item> newResult().data(new Item('first')).localContext('context-field').build()
        }

        CompletionStage<DataFetcherResult<Item>> asyncItem() {
            return CompletableFuture.completedFuture(
                    DataFetcherResult.<Item> newResult().data(new Item('async')).localContext('context-async').build()
            )
        }

        List<DataFetcherResult<Item>> items() {
            return ['a', 'b'].collect {
                DataFetcherResult.<Item> newResult().data(new Item(it)).localContext('context-' + it).build()
            }
        }

        DataFetcherResult<String> greeting() {
            return DataFetcherResult.<String> newResult()
                    .data('hello')
                    .error(GraphqlErrorBuilder.newError().message('partial failure').build())
                    .build()
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLTypeResolver(Item)
    static class ItemResolver {
        String label(Item item, DataFetchingEnvironment env) {
            return item.name + '@' + env.getLocalContext()
        }
    }

    @GraphQLType
    static class Item {
        final String name

        Item(String name) {
            this.name = name
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_NESTED)
    @GraphQLRootResolver
    static class NestedQuery {
        DataFetcherResult<DataFetcherResult<String>> greeting() {
            return null
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_LIST_FUTURE)
    @GraphQLRootResolver
    static class ListFutureQuery {
        List<CompletionStage<String>> names() {
            return null
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_INPUT)
    @GraphQLRootResolver
    static class InputQuery {
        String search(DataFetcherResult<FilterInput> filter) {
            return null
        }
    }

    @GraphQLInput
    static class FilterInput {
        String term
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_RAW)
    @GraphQLRootResolver
    static class RawQuery {
        DataFetcherResult greeting() {
            return null
        }
    }

}
