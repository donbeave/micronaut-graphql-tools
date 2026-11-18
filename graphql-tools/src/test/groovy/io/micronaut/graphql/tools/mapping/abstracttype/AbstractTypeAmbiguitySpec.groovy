package io.micronaut.graphql.tools.mapping.abstracttype

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.AmbiguousAbstractTypeException
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language
import spock.lang.Unroll

import static io.micronaut.graphql.tools.mapping.abstracttype.JavaAmbiguousMembers.Bagged
import static io.micronaut.graphql.tools.mapping.abstracttype.JavaAmbiguousMembers.Boxed
import static io.micronaut.graphql.tools.mapping.abstracttype.JavaAmbiguousMembers.BoxedAndBagged
import static io.micronaut.graphql.tools.mapping.abstracttype.JavaAmbiguousMembers.Marked
import static io.micronaut.graphql.tools.mapping.abstracttype.JavaAmbiguousMembers.MarkedAndStamped
import static io.micronaut.graphql.tools.mapping.abstracttype.JavaAmbiguousMembers.OnlyBoxed
import static io.micronaut.graphql.tools.mapping.abstracttype.JavaAmbiguousMembers.Stamped
import static io.micronaut.graphql.tools.mapping.abstracttype.JavaAmbiguousMembers.Wrapped

class AbstractTypeAmbiguitySpec extends AbstractTest {

    static final String UNION = "AbstractTypeAmbiguitySpec.union"
    static final String INTERFACE = "AbstractTypeAmbiguitySpec.interface"
    static final String CHAIN = "AbstractTypeAmbiguitySpec.chain"
    static final String EXACT = "AbstractTypeAmbiguitySpec.exact"

    @Language("GraphQL")
    static final String EXACT_SCHEMA = """
schema {
  query: Query
}

type Query {
  wrapped: Holder
}

union Holder = Marked | Stamped

type Marked {
  label: String!
}

type Stamped {
  label: String!
}
"""

    static String schema(String keyword) {
        boolean union = keyword == 'union'
        return """
schema {
  query: Query
}

type Query {
  wrapped: ${union ? 'Holder' : 'Container'}
}

${union ? 'union Holder = Boxed | Bagged' : 'interface Container {\n  label: String!\n}'}

type Boxed ${union ? '' : 'implements Container '}{
  label: String!
}

type Bagged ${union ? '' : 'implements Container '}{
  label: String!
}
"""
    }

    @Language("GraphQL")
    static final String CHAIN_SCHEMA = """
schema {
  query: Query
}

type Query {
  note: Note
}

union Note = Plain | Long

type Plain {
  body: String!
}

type Long {
  body: String!
}
"""

    @Unroll
    void "a class matching two unrelated members of a #kind fails the startup naming the class and both members"() {
        given:
        startContext(schema(kind), spec)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof AmbiguousAbstractTypeException
        e.cause.message == """[ambiguous-abstract-type] Objects of these classes cannot be resolved to one object type of a union or interface, because no single object type is the most specific one:
  ${BoxedAndBagged.name} is an instance of Bagged (${Bagged.name}), Boxed (${Boxed.name}) of ${kind == 'union' ? 'union Holder' : 'interface Container'}
Map each class to one of them with @GraphQLType(TheJavaInterface.class), or let it implement only one of their Java types."""
        e.cause.ambiguities.size() == 1

        where:
        kind        | spec
        'union'     | UNION
        'interface' | INTERFACE
    }

    void "a class mapped by its exact class to one of two ambiguous members does not fail the startup"() {
        given:
        startContext(EXACT_SCHEMA, EXACT)

        when:
        def result = executeQuery("{ wrapped { __typename } }")

        then:
        result.errors.isEmpty()
        result.data.wrapped == [__typename: 'Marked']
    }

    void "a class matching a member and its more specific sub-member does not fail the startup"() {
        given:
        startContext(CHAIN_SCHEMA, CHAIN)

        when:
        def result = executeQuery("{ note { __typename } }")

        then:
        result.errors.isEmpty()
        result.data.note == [__typename: 'Long']
    }

    interface Wrapper {
    }

    @io.micronaut.graphql.tools.annotation.GraphQLType
    static class PlainNote implements Wrapper {
        String body
    }

    @io.micronaut.graphql.tools.annotation.GraphQLType
    static class LongNote extends PlainNote {
    }

    @io.micronaut.graphql.tools.annotation.GraphQLType
    static class IntrospectedLongNote extends LongNote {
    }

    @Requires(property = 'spec.name', value = UNION)
    @GraphQLRootResolver
    static class UnionQuery {
        Wrapped wrapped() {
            return new OnlyBoxed()
        }
    }

    @Requires(property = 'spec.name', value = INTERFACE)
    @GraphQLRootResolver
    static class InterfaceQuery {
        Wrapped wrapped() {
            return new OnlyBoxed()
        }
    }

    @Requires(property = 'spec.name', pattern = 'AbstractTypeAmbiguitySpec\\.(union|interface)')
    @Factory
    static class WrappedDictionary {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (dictionary) -> dictionary
                    .registerType("Boxed", Boxed)
                    .registerType("Bagged", Bagged)
        }
    }

    @Requires(property = 'spec.name', value = EXACT)
    @GraphQLRootResolver
    static class ExactQuery {
        Wrapped wrapped() {
            return new MarkedAndStamped()
        }
    }

    @Requires(property = 'spec.name', value = EXACT)
    @Factory
    static class ExactDictionary {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (dictionary) -> dictionary
                    .registerType("Marked", Marked)
                    .registerType("Stamped", Stamped)
        }
    }

    @Requires(property = 'spec.name', value = CHAIN)
    @GraphQLRootResolver
    static class ChainQuery {
        Wrapper note() {
            return new IntrospectedLongNote(body: 'b')
        }
    }

    @Requires(property = 'spec.name', value = CHAIN)
    @Factory
    static class ChainDictionary {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (dictionary) -> dictionary
                    .registerType("Plain", PlainNote)
                    .registerType("Long", LongNote)
        }
    }

}
