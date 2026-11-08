package io.micronaut.graphql.tools.mapping.interfacetype

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.exceptions.AmbiguousBindingException
import io.micronaut.graphql.tools.exceptions.MultipleMethodsFoundException
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

/**
 * A {@link GraphQLTypeResolver} for the Java interface an SDL interface is mapped to binds the fields of that
 * interface on every implementing object type that does not bind them itself. An implementation that binds such a
 * field itself as well is ambiguous.
 */
class InterfaceFieldResolverSpec extends AbstractTest {

    static final String SPEC_NAME = "InterfaceFieldResolverSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  sections: [Section!]!
}

interface Section {
  id: String!
  title: String!
  summary: String!
}

type TextSection implements Section {
  id: String!
  title: String!
  summary: String!
  text: String!
}

type MediaSection implements Section {
  id: String!
  title: String!
  summary: String!
  url: String!
}
"""

    void "an interface field resolver binds the field on every implementation (#order)"() {
        given:
        startContext(schema, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    sections {
        __typename
        id
        summary
    }
    text { summary text }
}
""")

        then:
        result.errors.isEmpty()
        result.data.sections == [
                [__typename: 'TextSection', id: 'notes', summary: 'Summary of Notes'],
                [__typename: 'MediaSection', id: 'cover', summary: 'Summary of Cover']
        ]
        result.data.text == [summary: 'Summary of Notes', text: 'Some text']

        where:
        order                                                  | schema
        'the interface is reached first'                       | SCHEMA.replace('sections: [Section!]!\n}', 'sections: [Section!]!\n  text: TextSection\n}')
        'an implementation is reached before the interface' | SCHEMA.replace('type Query {\n  sections: [Section!]!', 'type Query {\n  text: TextSection\n  sections: [Section!]!')
    }

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '(\\.ownProperty|\\.ownResolver|\\.coordinate)?')
    @GraphQLRootResolver
    static class Query {
        List<Section> sections() {
            return [
                    new TextSection(id: 'notes', title: 'Notes', text: 'Some text'),
                    new MediaSection(id: 'cover', title: 'Cover', url: 'https://example.com/c.png')
            ]
        }

        TextSection text() {
            return new TextSection(id: 'notes', title: 'Notes', text: 'Some text')
        }
    }

    static interface Section {
        String getId()

        String getTitle()
    }

    @GraphQLType
    static class TextSection implements Section {
        String id
        String title
        String text
    }

    @GraphQLType
    static class MediaSection implements Section {
        String id
        String title
        String url
    }

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '(\\.ownProperty|\\.ownResolver)?')
    @GraphQLTypeResolver(Section.class)
    static class SectionResolver {
        String summary(Section section) {
            return "Summary of " + section.title
        }
    }

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '(\\.ownResolver|\\.coordinate)?')
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

    // --- an implementation that binds the field itself as well ---

    void "an implementation with a property for a field that an interface resolver binds fails the startup"() {
        given:
        startContext(SCHEMA, SPEC_NAME + ".ownProperty")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof AmbiguousBindingException
        e.cause.message == """Both a member of the Java type and a type resolver method can bind one GraphQL field.
  GraphQL object type: TextSection
  GraphQL field: summary
  Members:
  1) ${SectionResolver.name} summary(${Section.name} section)
  2) ${SummarizedTextSection.name} getSummary()
  Put a directive listed in micronaut.graphql.tools.resolver-directives on the field to bind the type resolver method, remove one of the members, or set micronaut.graphql.tools.precedence to property-first or resolver-first."""
    }

    @GraphQLType
    static class SummarizedTextSection implements Section {
        String id
        String title
        String text
        String summary
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".ownProperty")
    @Factory
    static class OwnPropertyDictionaryFactory {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (schemaMappingDictionary) -> schemaMappingDictionary
                    .registerType("TextSection", SummarizedTextSection.class)
                    .registerType("MediaSection", MediaSection.class)
        }
    }

    void "an implementation with its own type resolver for a field that an interface resolver binds fails the startup"() {
        given:
        startContext(SCHEMA, SPEC_NAME + ".ownResolver")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof MultipleMethodsFoundException
        e.cause.message == """Found multiple methods for one GraphQL field.
  GraphQL object type: MediaSection
  GraphQL field: summary
  Methods:\u0020
  1) ${MediaSectionResolver.name} summary(${MediaSection.name} section)
  2) ${SectionResolver.name} summary(${Section.name} section)"""
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".ownResolver")
    @GraphQLTypeResolver(MediaSection.class)
    static class MediaSectionResolver {
        String summary(MediaSection section) {
            return "Media summary of " + section.title
        }
    }

    // --- an object type that implements two interfaces with a resolver for the same field ---

    void "two interface resolvers for the same field of an implementation fail the startup"() {
        given:
        startContext("""
schema {
  query: Query
}

type Query {
  items: [Item!]!
  labelled: [Labelled!]!
}

interface Item {
  id: String!
  label: String!
}

interface Labelled {
  label: String!
}

type Tag implements Item & Labelled {
  id: String!
  label: String!
}
""", SPEC_NAME + ".twoInterfaces")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof MultipleMethodsFoundException
        e.cause.message == """Found multiple methods for one GraphQL field.
  GraphQL object type: Tag
  GraphQL field: label
  Methods:\u0020
  1) ${ItemResolver.name} label(${Item.name} item)
  2) ${LabelledResolver.name} label(${Labelled.name} labelled)"""
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".twoInterfaces")
    @GraphQLRootResolver
    static class TagQuery {
        List<Item> items() {
            return []
        }

        List<Labelled> labelled() {
            return []
        }
    }

    static interface Item {
        String getId()
    }

    static interface Labelled {
    }

    @GraphQLType
    static class Tag implements Item, Labelled {
        String id
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".twoInterfaces")
    @GraphQLTypeResolver(Item.class)
    static class ItemResolver {
        String label(Item item) {
            return 'item'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".twoInterfaces")
    @GraphQLTypeResolver(Labelled.class)
    static class LabelledResolver {
        String label(Labelled labelled) {
            return 'labelled'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".twoInterfaces")
    @Factory
    static class TagDictionaryFactory {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (schemaMappingDictionary) -> schemaMappingDictionary
                    .registerType("Tag", Tag.class)
        }
    }

    // --- a resolver of an interface that another interface implements ---

    void "a resolver of an implemented interface binds the field on the object types"() {
        given:
        startContext("""
schema {
  query: Query
}

type Query {
  sections: [Section!]!
  node(id: String!): Node
}

interface Node {
  id: String!
  label: String!
}

interface Section implements Node {
  id: String!
  label: String!
  title: String!
}

type TextSection implements Section & Node {
  id: String!
  label: String!
  title: String!
  text: String!
}
""", SPEC_NAME + ".nested")

        when:
        def result = executeQuery("""
{
    sections { id label }
    node(id: "notes") { id label }
}
""")

        then:
        result.errors.isEmpty()
        result.data.sections == [[id: 'notes', label: 'Node notes']]
        result.data.node == [id: 'notes', label: 'Node notes']
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".nested")
    @GraphQLRootResolver
    static class NestedQuery {
        List<NestedSection> sections() {
            return [new NestedTextSection(id: 'notes', title: 'Notes', text: 'Some text')]
        }

        Node node(String id) {
            return new NestedTextSection(id: id, title: 'Notes', text: 'Some text')
        }
    }

    static interface Node {
        String getId()
    }

    static interface NestedSection extends Node {
        String getTitle()
    }

    @GraphQLType
    static class NestedTextSection implements NestedSection {
        String id
        String title
        String text
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".nested")
    @GraphQLTypeResolver(Node.class)
    static class NodeResolver {
        String label(Node node) {
            return 'Node ' + node.id
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".nested")
    @Factory
    static class NestedDictionaryFactory {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (schemaMappingDictionary) -> schemaMappingDictionary
                    .registerType("TextSection", NestedTextSection.class)
        }
    }

    // --- a schema coordinate on an interface resolver names the interface field ---

    void "an interface resolver method may carry the coordinate of the interface field"() {
        given:
        startContext(SCHEMA, SPEC_NAME + ".coordinate")

        when:
        def result = executeQuery("{ sections { summary } }")

        then:
        result.errors.isEmpty()
        result.data.sections == [[summary: 'Contract summary of Notes'], [summary: 'Contract summary of Cover']]
    }

    @GraphQLTypeResolver(Section.class)
    static interface SectionResolverContract {
        @GraphQLSchemaCoordinate("Section.summary")
        String summary(Section section)
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".coordinate")
    @Singleton
    static class ContractSectionResolver implements SectionResolverContract {
        @Override
        String summary(Section section) {
            return "Contract summary of " + section.title
        }
    }

}
