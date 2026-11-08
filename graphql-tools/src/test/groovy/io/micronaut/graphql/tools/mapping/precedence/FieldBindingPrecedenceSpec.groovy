package io.micronaut.graphql.tools.mapping.precedence

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLField
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.exceptions.AmbiguousBindingException
import io.micronaut.graphql.tools.exceptions.ResolverRequiredException
import org.intellij.lang.annotations.Language

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicInteger

/**
 * A field that both a getter of the Java type and a type resolver method can bind: the getter returns {@code null}
 * (the value is computed elsewhere), the type resolver method computes it, and the field is non-null.
 */
class FieldBindingPrecedenceSpec extends AbstractTest {

    static final String SPEC_NAME = "FieldBindingPrecedenceSpec"

    static final AtomicInteger RESOLVER_CALLS = new AtomicInteger()

    @Language("GraphQL")
    static final String MARKED_SCHEMA = """
schema {
  query: Query
}

directive @computed on FIELD_DEFINITION

scalar Long

type Query {
  items: [Item!]!
}

type Item {
  name: String!
  price: Long! @computed
}
"""

    @Language("GraphQL")
    static final String UNMARKED_SCHEMA = """
schema {
  query: Query
}

directive @computed on FIELD_DEFINITION

scalar Long

type Query {
  items: [Item!]!
}

type Item {
  name: String!
  price: Long!
}
"""

    void setup() {
        RESOLVER_CALLS.set(0)
    }

    void "a field marked with a resolver directive binds the type resolver method, not the getter"() {
        given:
        startContext(MARKED_SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.resolver-directives': ['computed']])

        when:
        def result = executeQuery('{ items { name price } }')

        then:
        result.errors.isEmpty()
        result.data.items == [[name: 'pen', price: 3L], [name: 'ink', price: 3L]]
        RESOLVER_CALLS.get() == 2
    }

    void "without the directive, a getter and a type resolver method for one field fail the startup"() {
        given:
        startContext(UNMARKED_SCHEMA, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof AmbiguousBindingException
        e.cause.message == """Both a member of the Java type and a type resolver method can bind one GraphQL field.
  GraphQL coordinate: Item.price (line 16, column 3)
  GraphQL object type: Item
  GraphQL field: price
  Members:
  1) ${Item.name} getPrice()
  2) ${ItemResolver.name} price(${Item.name} item)
  Put a directive listed in micronaut.graphql.tools.resolver-directives on the field to bind the type resolver method, remove one of the members, or set micronaut.graphql.tools.precedence to property-first or resolver-first."""
        e.cause.mappingContext.graphQlObjectType == 'Item'
        e.cause.mappingContext.graphQlField == 'price'
        e.cause.members.keySet() == [Item, ItemResolver] as Set
    }

    void "precedence strict can be set explicitly"() {
        given:
        startContext(UNMARKED_SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.precedence': 'strict'])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof AmbiguousBindingException
    }

    void "precedence property-first binds the getter and never calls the type resolver method, as before the option"() {
        given:
        startContext(UNMARKED_SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.precedence': 'property-first'])

        when:
        def result = executeQuery('{ items { name price } }')

        then: 'the getter returns null for a non-null field'
        result.data == null
        !result.errors.isEmpty()
        result.errors*.message.every { it.contains('non null') }
        RESOLVER_CALLS.get() == 0
    }

    void "precedence resolver-first binds the type resolver method"() {
        given:
        startContext(UNMARKED_SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.precedence': 'resolver-first'])

        when:
        def result = executeQuery('{ items { name price } }')

        then:
        result.errors.isEmpty()
        result.data.items == [[name: 'pen', price: 3L], [name: 'ink', price: 3L]]
        RESOLVER_CALLS.get() == 2
    }

    void "the directive wins over precedence property-first"() {
        given:
        startContext(MARKED_SCHEMA, SPEC_NAME, [
                'micronaut.graphql.tools.resolver-directives': ['computed'],
                'micronaut.graphql.tools.precedence'         : 'property-first'
        ])

        when:
        def result = executeQuery('{ items { price } }')

        then:
        result.errors.isEmpty()
        result.data.items == [[price: 3L], [price: 3L]]
    }

    void "a directive that is not listed has no effect"() {
        given:
        startContext(MARKED_SCHEMA, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof AmbiguousBindingException
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        List<Item> items() {
            return [new Item(name: 'pen'), new Item(name: 'ink')]
        }
    }

    @GraphQLType
    static class Item {
        String name
        Long price
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLTypeResolver(Item.class)
    static class ItemResolver {
        CompletionStage<Long> price(Item item) {
            RESOLVER_CALLS.incrementAndGet()
            return CompletableFuture.completedFuture(3L)
        }
    }

    // --- a field marked with the directive that only a getter binds ---

    void "a field marked with a resolver directive fails the startup when only a getter binds it"() {
        given:
        startContext(MARKED_SCHEMA, SPEC_NAME + ".getterOnly", ['micronaut.graphql.tools.resolver-directives': ['computed']])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof ResolverRequiredException
        e.cause.message == """The GraphQL field carries @computed, so a type resolver method must bind it, but none does.
  GraphQL coordinate: Item.price (line 16, column 3)
  GraphQL object type: Item
  GraphQL field: price
  Not bound:
  1) ${Item.name} getPrice()
  Add a type resolver method for the field, or remove @computed from it."""
        e.cause.directive == 'computed'
    }

    void "without the directive a getter alone binds the field"() {
        given:
        startContext(UNMARKED_SCHEMA, SPEC_NAME + ".getterOnly")

        when:
        def result = executeQuery('{ items { name } }')

        then:
        result.errors.isEmpty()
        result.data.items == [[name: 'pen'], [name: 'ink']]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".getterOnly")
    @GraphQLRootResolver
    static class GetterOnlyQuery {
        List<Item> items() {
            return [new Item(name: 'pen', price: 1L), new Item(name: 'ink', price: 2L)]
        }
    }

    // --- a @GraphQLField method of the Java type and a type resolver method ---

    void "a @GraphQLField method and a type resolver method for one field fail the startup"() {
        given:
        startContext("""
schema {
  query: Query
}

type Query {
  label: Label
}

type Label {
  text(upper: Boolean): String
}
""", SPEC_NAME + ".method")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof AmbiguousBindingException
        e.cause.members == [
                (Label)        : ["text(java.lang.Boolean upper)"],
                (LabelResolver): ["text(${Label.name} label, java.lang.Boolean upper)".toString()]
        ]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".method")
    @GraphQLRootResolver
    static class LabelQuery {
        Label label() {
            return new Label()
        }
    }

    @GraphQLType
    static class Label {
        @GraphQLField
        String text(Boolean upper) {
            return upper ? 'TEXT' : 'text'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".method")
    @GraphQLTypeResolver(Label.class)
    static class LabelResolver {
        String text(Label label, Boolean upper) {
            return 'resolved'
        }
    }

    // --- configuration ---

    void "a resolver directive that the schema does not declare fails the startup"() {
        given:
        startContext(UNMARKED_SCHEMA, SPEC_NAME + ".getterOnly", ['micronaut.graphql.tools.resolver-directives': ['computed', 'missing', 'absent']])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof ConfigurationException
        e.cause.message == "micronaut.graphql.tools.resolver-directives lists directives that the schema does not declare: absent, missing."
    }

}
