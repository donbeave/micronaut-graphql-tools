package io.micronaut.graphql.tools.mapping.interfacetype

import graphql.schema.idl.errors.SchemaProblem
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.SchemaMappingDictionary
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.exceptions.IncorrectClassMappingException
import io.micronaut.graphql.tools.exceptions.InterfaceTypeMappingNotProvidedException
import io.micronaut.graphql.tools.exceptions.MethodNotFoundException
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

/**
 * A field that returns an SDL interface binds every object type implementing it, each through the class registered
 * in the {@link SchemaMappingDictionary}; the runtime object is resolved by its exact class.
 */
class InterfaceTypeSpec extends AbstractTest {

    static final String SPEC_NAME = "InterfaceTypeSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  book(id: ID!): Book
}

type Book {
  id: ID!
  title: String
  sections: [Section!]!
}

interface Section {
  id: String!
  title: String!
}

type TextSection implements Section {
  id: String!
  title: String!
  text: String!
}

type MediaSection implements Section {
  id: String!
  title: String!
  url: String!
}
"""

    void "an interface field binds every implementation, and the runtime object resolves to its object type"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    book(id: "1") {
        id
        sections {
            __typename
            id
            title
            ... on TextSection { text }
            ... on MediaSection { url }
        }
    }
}
""")

        then:
        result.errors.isEmpty()
        result.data.book == [
                id      : '1',
                sections: [
                        [__typename: 'TextSection', id: 'notes', title: 'Notes', text: 'Some text'],
                        [__typename: 'MediaSection', id: 'cover', title: 'Cover', url: 'https://example.com/c.png']
                ]
        ]
    }

    void "an object of a class that no implementation is mapped to is a GraphQL error naming the interface, the path and the class"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    book(id: "unknown") {
        id
        sections { id }
    }
}
""")

        then:
        result.errors.size() == 1
        result.errors[0].path == ['book', 'sections', 1]
        // the middle part is graphql-java's ("for field '[Section!].sections'" for a list element)
        result.errors[0].message.startsWith("Can't resolve '/book/sections[1]'. Abstract type 'Section' must " +
                "resolve to an Object type at runtime for field ")
        result.errors[0].message.endsWith(". The object of class ${UnknownSection.name} is none of the object " +
                "types of interface Section: MediaSection (${MediaSection.name}), TextSection (${TextSection.name}).")
        result.data.book == null
    }

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '.*')
    @GraphQLRootResolver
    static class Query {
        Book book(String id) {
            return new Book(id: id)
        }
    }

    @GraphQLType
    static class Book {
        String id
        String title
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLTypeResolver(Book.class)
    static class BookResolver {
        List<Section> sections(Book book) {
            if (book.id == 'unknown') {
                return [new TextSection(id: 'notes', title: 'Notes', text: 'Some text'), new UnknownSection()]
            }
            return [
                    new TextSection(id: 'notes', title: 'Notes', text: 'Some text'),
                    new MediaSection(id: 'cover', title: 'Cover', url: 'https://example.com/c.png')
            ]
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

    static class UnknownSection implements Section {
        String id = 'unknown'
        String title = 'Unknown'
    }

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '(\\.wrongJavaType)?')
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

    // --- startup errors ---

    void "an interface field mapped to a class instead of a Java interface fails the startup"() {
        given:
        startContext(SCHEMA, SPEC_NAME + ".wrongJavaType")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof IncorrectClassMappingException
        e.cause.message == """The field is mapped to a custom class, when required an interface.
  GraphQL object type: Book
  GraphQL field: sections
  Mapped class: ${ConcreteSectionsResolver.name}
  Mapped method: sections(${Book.name} book)
  Provided class: ${TextSection.name}"""
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".wrongJavaType")
    @GraphQLTypeResolver(Book.class)
    static class ConcreteSectionsResolver {
        List<TextSection> sections(Book book) {
            return []
        }
    }

    void "an implementation without a class in the dictionary fails the startup"() {
        given:
        startContext(SCHEMA, SPEC_NAME + ".notRegistered")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof InterfaceTypeMappingNotProvidedException
        e.cause.message == """Can not detect representation class for type MediaSection, implementation of Section interface. Ensure the representation class is registered via ${SchemaMappingDictionary.name}.
  GraphQL object type: Book
  GraphQL field: sections
  Mapped class: ${NotRegisteredSectionsResolver.name}
  Mapped method: sections(${Book.name} book)"""
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".notRegistered")
    @GraphQLTypeResolver(Book.class)
    static class NotRegisteredSectionsResolver {
        List<Section> sections(Book book) {
            return []
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".notRegistered")
    @Factory
    static class PartialDictionaryFactory {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (schemaMappingDictionary) -> schemaMappingDictionary
                    .registerType("TextSection", TextSection.class)
        }
    }

    void "every field of the interface is bound on every implementation"() {
        given:
        startContext(SCHEMA, SPEC_NAME + ".implementationMissingField")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof MethodNotFoundException
        e.cause.message == """The property or method `title` not found in ${UntitledSection.name}'s type.
  GraphQL object type: MediaSection
  GraphQL field: title"""
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".implementationMissingField")
    @GraphQLTypeResolver(Book.class)
    static class UntitledSectionsResolver {
        List<Section> sections(Book book) {
            return []
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".implementationMissingField")
    @Factory
    static class UntitledDictionaryFactory {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (schemaMappingDictionary) -> schemaMappingDictionary
                    .registerType("TextSection", TextSection.class)
                    .registerType("MediaSection", UntitledSection.class)
        }
    }

    @GraphQLType
    static class UntitledSection {
        String id
        String url
    }

    void "an SDL implementation that lacks a field of its interface is reported by graphql-java"() {
        given:
        startContext("""
schema {
  query: Query
}

type Query {
  book(id: ID!): Book
}

type Book {
  id: ID!
  title: String
  sections: [Section!]!
}

interface Section {
  id: String!
  title: String!
}

type TextSection implements Section {
  id: String!
  title: String!
  text: String!
}

type MediaSection implements Section {
  id: String!
  url: String!
}
""", SPEC_NAME + ".sdlMissingField")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaProblem
        e.cause.message.contains("The object type 'MediaSection' [@")
        e.cause.message.contains("does not have a field 'title' required via interface 'Section' [@")
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".sdlMissingField")
    @GraphQLTypeResolver(Book.class)
    static class SdlMissingFieldResolver {
        List<Section> sections(Book book) {
            return []
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".sdlMissingField")
    @Factory
    static class SdlMissingFieldDictionaryFactory {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (schemaMappingDictionary) -> schemaMappingDictionary
                    .registerType("TextSection", TextSection.class)
                    .registerType("MediaSection", MediaSection.class)
        }
    }

}
