package io.micronaut.graphql.tools.mapping.precedence

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.exceptions.AmbiguousBindingException
import io.micronaut.graphql.tools.exceptions.ResolverRequiredException
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

/**
 * The precedence between a getter of an implementation and a type resolver of the Java interface an SDL interface is
 * mapped to, for a field that the interface declares.
 */
class InterfaceFieldPrecedenceSpec extends AbstractTest {

    static final String SPEC_NAME = "InterfaceFieldPrecedenceSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

directive @computed on FIELD_DEFINITION

type Query {
  sections: [Section!]!
  text: TextSection
}

interface Section {
  title: String!
  summary: String!
}

type TextSection implements Section {
  title: String!
  summary: String! @computed
}

type MediaSection implements Section {
  title: String!
  summary: String!
}
"""

    void "a getter of an implementation and an interface resolver for one field fail the startup (#order)"() {
        given:
        startContext(schema, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof AmbiguousBindingException
        e.cause.message.startsWith("""Both a member of the Java type and a type resolver method can bind one GraphQL field.
  GraphQL coordinate: TextSection.summary (line ${line}, column 3)
  GraphQL object type: TextSection
  GraphQL field: summary
  Members:
  1) ${SectionResolver.name} summary(${Section.name} section)
  2) ${TextSection.name} getSummary()
""")

        where:
        order                                 | line | schema
        'the interface is reached first'      | 20   | SCHEMA
        'the implementation is reached first' | 20   | SCHEMA.replace('sections: [Section!]!\n  text: TextSection', 'text: TextSection\n  sections: [Section!]!')
    }

    void "a field marked with a resolver directive binds the interface resolver, not the getter (#order)"() {
        given:
        startContext(schema, SPEC_NAME, ['micronaut.graphql.tools.resolver-directives': ['computed']])

        when:
        def result = executeQuery('{ sections { __typename title summary } }')

        then:
        result.errors.isEmpty()
        result.data.sections == [
                [__typename: 'TextSection', title: 'Notes', summary: 'Summary of Notes'],
                [__typename: 'MediaSection', title: 'Cover', summary: 'Summary of Cover']
        ]

        where:
        order                               | schema
        'the interface is reached first'    | SCHEMA
        'the implementation is reached first' | SCHEMA.replace('sections: [Section!]!\n  text: TextSection', 'text: TextSection\n  sections: [Section!]!')
    }

    void "precedence property-first binds the getter of the implementation"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.precedence': 'property-first',
                                        'micronaut.graphql.tools.inert-directives': ['computed']])

        when:
        def result = executeQuery('{ sections { title summary } }')

        then:
        result.errors.isEmpty()
        result.data.sections == [
                [title: 'Notes', summary: 'Own summary'],
                [title: 'Cover', summary: 'Summary of Cover']
        ]
    }

    void "precedence resolver-first binds the interface resolver (#order)"() {
        given:
        startContext(schema, SPEC_NAME, ['micronaut.graphql.tools.precedence': 'resolver-first',
                                        'micronaut.graphql.tools.inert-directives': ['computed']])

        when:
        def result = executeQuery('{ sections { title summary } }')

        then:
        result.errors.isEmpty()
        result.data.sections == [
                [title: 'Notes', summary: 'Summary of Notes'],
                [title: 'Cover', summary: 'Summary of Cover']
        ]

        where:
        order                               | schema
        'the interface is reached first'    | SCHEMA
        'the implementation is reached first' | SCHEMA.replace('sections: [Section!]!\n  text: TextSection', 'text: TextSection\n  sections: [Section!]!')
    }

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '(\\.noResolver)?')
    @GraphQLRootResolver
    static class Query {
        List<Section> sections() {
            return [
                    new TextSection(title: 'Notes', summary: 'Own summary'),
                    new MediaSection(title: 'Cover')
            ]
        }

        TextSection text() {
            return new TextSection(title: 'Notes', summary: 'Own summary')
        }
    }

    static interface Section {
        String getTitle()
    }

    @GraphQLType
    static class TextSection implements Section {
        String title
        String summary
    }

    @GraphQLType
    static class MediaSection implements Section {
        String title
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLTypeResolver(Section.class)
    static class SectionResolver {
        String summary(Section section) {
            return "Summary of " + section.title
        }
    }

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '(\\.noResolver)?')
    @Factory
    static class DictionaryFactory {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (schemaMappingDictionary) -> schemaMappingDictionary
                    .registerType("TextSection", TextSection.class)
                    .registerType("MediaSection", MediaSection.class)
        }
    }

    // --- a field marked with the directive that no resolver binds ---

    void "a field marked with a resolver directive fails the startup when only a getter of the implementation binds it"() {
        given:
        startContext(SCHEMA.replace('\ntype MediaSection implements Section {\n  title: String!\n  summary: String!\n}\n', ''),
                SPEC_NAME + ".noResolver", ['micronaut.graphql.tools.resolver-directives': ['computed']])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof ResolverRequiredException
        e.cause.message.startsWith("""The GraphQL field carries @computed, so a type resolver method must bind it, but none does.
  GraphQL coordinate: TextSection.summary (line 20, column 3)
  GraphQL object type: TextSection
  GraphQL field: summary
  Not bound:
  1) ${TextSection.name} getSummary()""")
    }

}
