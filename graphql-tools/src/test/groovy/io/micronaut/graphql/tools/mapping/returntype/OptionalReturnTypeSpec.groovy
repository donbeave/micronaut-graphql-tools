package io.micronaut.graphql.tools.mapping.returntype

import graphql.execution.DataFetcherResult
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.exceptions.UnsupportedReturnTypeException
import org.intellij.lang.annotations.Language

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * A declared return type may wrap the mapped class in {@link Optional} when the GraphQL type is nullable; an empty
 * {@link Optional} becomes {@code null}. On a non-null GraphQL type an {@link Optional} fails at startup.
 */
class OptionalReturnTypeSpec extends AbstractTest {

    static final String SPEC_NAME = "OptionalReturnTypeSpec"
    static final String SPEC_NAME_NON_NULL = "OptionalReturnTypeSpecNonNull"
    static final String SPEC_NAME_NON_NULL_ELEMENT = "OptionalReturnTypeSpecNonNullElement"
    static final String SPEC_NAME_WRONG_ORDER = "OptionalReturnTypeSpecWrongOrder"
    static final String SPEC_NAME_NON_NULL_PROPERTY = "OptionalReturnTypeSpecNonNullProperty"

    void "Optional on nullable fields: property, method, inside CompletionStage and DataFetcherResult, per list element"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  present: String
  absent: String
  asyncItem: Item
  wrapped: String
  names: [String]!
}

type Item {
  name: String!
  note: String
  missingNote: String
}
"""
        startContext(schema, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    present
    absent
    asyncItem { name note missingNote }
    wrapped
    names
}
""")

        then:
        result.errors.isEmpty()
        result.data.present == 'here'
        result.data.containsKey('absent')
        result.data.absent == null
        result.data.asyncItem == [name: 'item', note: 'a note', missingNote: null]
        result.data.wrapped == 'wrapped'
        result.data.names == ['a', null, 'c']
    }

    void "Optional on a non-null field fails at startup"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  name: String!
}
"""
        startContext(schema, SPEC_NAME_NON_NULL)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnsupportedReturnTypeException
        e.cause.message == """The return type of the method is not supported for this field: ${Optional.name} is only allowed for a nullable GraphQL type, the field type is String!.
  GraphQL object type: Query
  GraphQL field: name
  Mapped class: ${NonNullQuery.name}
  Mapped method: name()"""
        e.cause.mappingContext.graphQlObjectType == 'Query'
        e.cause.mappingContext.graphQlField == 'name'
        e.cause.mappingContext.mappedMethod == 'name()'
    }

    void "Optional on a bean property of a non-null field fails at startup"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  holder: Holder
}

type Holder {
  note: String!
}
"""
        startContext(schema, SPEC_NAME_NON_NULL_PROPERTY)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnsupportedReturnTypeException
        e.cause.message == """The return type of the method is not supported for this field: ${Optional.name} is only allowed for a nullable GraphQL type, the field type is String!.
  GraphQL object type: Holder
  GraphQL field: note
  Mapped class: ${Holder.name}
  Mapped method: getNote()"""
    }

    void "Optional for a non-null list element fails at startup"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  names: [String!]
}
"""
        startContext(schema, SPEC_NAME_NON_NULL_ELEMENT)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnsupportedReturnTypeException
        e.cause.message.startsWith("The return type of the method is not supported for this field: " +
                "${Optional.name} is only allowed for a nullable GraphQL type, the field type is String!.")
        e.cause.message.contains("GraphQL field: names")
    }

    void "Optional wrapping a DataFetcherResult fails at startup"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  name: String
}
"""
        startContext(schema, SPEC_NAME_WRONG_ORDER)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnsupportedReturnTypeException
        e.cause.message.startsWith("The return type of the method is not supported for this field: " +
                "${Optional.name} cannot wrap ${DataFetcherResult.name}.")
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        Optional<String> present() {
            return Optional.of('here')
        }

        Optional<String> absent() {
            return Optional.empty()
        }

        CompletionStage<Optional<Item>> asyncItem() {
            return CompletableFuture.completedFuture(Optional.of(new Item()))
        }

        DataFetcherResult<Optional<String>> wrapped() {
            return DataFetcherResult.<Optional<String>> newResult().data(Optional.of('wrapped')).build()
        }

        List<Optional<String>> names() {
            return [Optional.of('a'), Optional.empty(), Optional.of('c')]
        }
    }

    @GraphQLType
    static class Item {
        String name = 'item'

        Optional<String> getNote() {
            return Optional.of('a note')
        }

        Optional<String> getMissingNote() {
            return Optional.empty()
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_NON_NULL)
    @GraphQLRootResolver
    static class NonNullQuery {
        Optional<String> name() {
            return Optional.of('name')
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_NON_NULL_PROPERTY)
    @GraphQLRootResolver
    static class NonNullPropertyQuery {
        Holder holder() {
            return new Holder()
        }
    }

    @GraphQLType
    static class Holder {
        Optional<String> getNote() {
            return Optional.of('note')
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_NON_NULL_ELEMENT)
    @GraphQLRootResolver
    static class NonNullElementQuery {
        List<Optional<String>> names() {
            return []
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_WRONG_ORDER)
    @GraphQLRootResolver
    static class WrongOrderQuery {
        Optional<DataFetcherResult<String>> name() {
            return Optional.empty()
        }
    }

}
