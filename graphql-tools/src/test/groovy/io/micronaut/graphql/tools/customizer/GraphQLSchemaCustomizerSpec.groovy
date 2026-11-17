package io.micronaut.graphql.tools.customizer

import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.SchemaTransformer
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import graphql.schema.GraphQLTypeVisitorStub
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.core.annotation.Order
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.GraphQLSchemaCustomizer
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

/**
 * {@link GraphQLSchemaCustomizer} beans change the schema the library built, lowest order first, each one
 * receiving the result of the previous one; the {@code GraphQL} bean runs the last result.
 */
class GraphQLSchemaCustomizerSpec extends AbstractTest {

    static final String SPEC_NAME = "GraphQLSchemaCustomizerSpec"
    static final String SPEC_NAME_NULL = "GraphQLSchemaCustomizerSpec.null"
    static final String SPEC_NAME_LISTENER = "GraphQLSchemaCustomizerSpec.listener"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  hello: String
  payload: Payload
}

union Payload = Item | Other

type Item {
  name: String
}

type Other {
  code: Int
}
"""

    void "customizers change the built schema in order, and the GraphQL bean runs the result"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery('{ __schema { description } hello payload { ... on Item { name } } }')

        then:
        result.errors.isEmpty()
        result.data == [__schema: [description: 'first, second'], hello: 'world', payload: [name: 'item']]
    }

    void "a type resolver resolves against the schema a BeanCreatedEventListener replaced it with"() {
        given:
        startContext(SCHEMA, SPEC_NAME_LISTENER)

        when: "the listener rebuilt every object type, so none of them is the one the library bound"
        def result = executeQuery('{ payload { ... on Item { name } ... on Other { code } } item: __type(name: "Item") { description } }')

        then:
        result.errors.isEmpty()
        result.data == [payload: [name: 'item'], item: [description: 'replaced']]
    }

    void "a customizer that returns null fails the startup"() {
        given:
        startContext(SCHEMA, SPEC_NAME_NULL)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof NullPointerException
        e.cause.message == "GraphQLSchemaCustomizer ${ReturnNull.name} returned null instead of a schema".toString()
    }

    // declared first, runs second
    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @Order(20)
    static class Second implements GraphQLSchemaCustomizer {
        @Override
        GraphQLSchema customize(GraphQLSchema schema) {
            return schema.transform { it.description(schema.description + ', second') }
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @Order(10)
    static class First implements GraphQLSchemaCustomizer {
        @Override
        GraphQLSchema customize(GraphQLSchema schema) {
            assert schema.description == null
            return schema.transform { it.description('first') }
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_NULL)
    @Singleton
    static class ReturnNull implements GraphQLSchemaCustomizer {
        @Override
        GraphQLSchema customize(GraphQLSchema schema) {
            return null
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_LISTENER)
    @Singleton
    static class ReplaceSchema implements BeanCreatedEventListener<GraphQLSchema> {
        @Override
        GraphQLSchema onCreated(BeanCreatedEvent<GraphQLSchema> event) {
            return new SchemaTransformer().transformSchema(event.bean, new GraphQLTypeVisitorStub() {
                @Override
                TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<graphql.schema.GraphQLSchemaElement> context) {
                    return changeNode(context, node.transform { it.description('replaced') })
                }
            })
        }
    }

    @Requires(property = 'spec.name', pattern = 'GraphQLSchemaCustomizerSpec.*')
    @GraphQLRootResolver
    static class Query {
        String hello() {
            return 'world'
        }

        Payload payload() {
            return new Item()
        }
    }

    static interface Payload {
    }

    @GraphQLType
    static class Item implements Payload {
        String name = 'item'
    }

    @GraphQLType
    static class Other implements Payload {
        Integer code = 1
    }

    @Requires(property = 'spec.name', pattern = 'GraphQLSchemaCustomizerSpec.*')
    @Factory
    static class DictionaryFactory {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (dictionary) -> dictionary
                    .registerType('Item', Item.class)
                    .registerType('Other', Other.class)
        }
    }

}
