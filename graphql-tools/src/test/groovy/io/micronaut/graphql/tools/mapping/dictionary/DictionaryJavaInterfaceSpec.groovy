package io.micronaut.graphql.tools.mapping.dictionary

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.exceptions.AmbiguousTypeMappingException
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

/**
 * The SchemaMappingDictionary maps GraphQL types to Java interfaces, and an interface and the classes annotated with
 * {@code @GraphQLType(interface)} are one mapping, not two.
 */
class DictionaryJavaInterfaceSpec extends AbstractTest {

    static final String UNION_MEMBER = "DictionaryJavaInterfaceSpec.unionMember"
    static final String INTERFACE_IMPLEMENTATION = "DictionaryJavaInterfaceSpec.interfaceImplementation"
    static final String FIELD_IMPLEMENTATION = "DictionaryJavaInterfaceSpec.fieldImplementation"
    static final String FIELD_INTERFACE = "DictionaryJavaInterfaceSpec.fieldInterface"
    static final String BOTH_FIELDS = "DictionaryJavaInterfaceSpec.bothFields"
    static final String ANNOTATED_IMPLEMENTATION = "DictionaryJavaInterfaceSpec.annotatedImplementation"
    static final String IMPLEMENTING_CLASS = "DictionaryJavaInterfaceSpec.implementingClass"
    static final String SUBCLASS = "DictionaryJavaInterfaceSpec.subclass"

    @Language("GraphQL")
    static final String UNION_SCHEMA = """
schema {
  query: Query
}

type Query {
  search(book: Boolean!): SearchResult
}

union SearchResult = Book | Author

type Book {
  title: String!
}

type Author {
  name: String!
}
"""

    @Language("GraphQL")
    static final String INTERFACE_SCHEMA = """
schema {
  query: Query
}

type Query {
  shelf: [Entry!]!
}

interface Entry {
  title: String!
}

type Poem implements Entry {
  title: String!
}

type Letter implements Entry {
  title: String!
}
"""

    @Language("GraphQL")
    static final String BOOK_SCHEMA = """
schema {
  query: Query
}

type Query {
  book: Book
}

type Book {
  title: String!
}
"""

    @Language("GraphQL")
    static final String TWO_FIELDS_SCHEMA = """
schema {
  query: Query
}

type Query {
  book: Book
  latestBook: Book
}

type Book {
  title: String!
}
"""

    @Language("GraphQL")
    static final String BOOK_OR_NOVEL_SCHEMA = """
schema {
  query: Query
}

type Query {
  search(book: Boolean!): SearchResult
}

union SearchResult = Book | Novel

type Book {
  title: String!
}

type Novel {
  title: String!
}
"""

    @Language("GraphQL")
    static final String ESSAY_SCHEMA = """
schema {
  query: Query
}

type Query {
  essay(long: Boolean!): EssayResult
}

union EssayResult = Essay | LongEssay

type Essay {
  title: String!
}

type LongEssay {
  title: String!
}
"""

    void "a union member mapped to a Java interface resolves to the class annotated with @GraphQLType(interface)"() {
        given:
        startContext(UNION_SCHEMA, UNION_MEMBER)

        when:
        def result = executeQuery("""
{
    book: search(book: true) {
        ... on Book { title }
        ... on Author { name }
    }
    author: search(book: false) {
        ... on Book { title }
        ... on Author { name }
    }
}
""")

        then:
        result.errors.isEmpty()
        result.data.book == [title: 'Dune']
        result.data.author == [name: 'Herbert']
    }

    void "an implementation of an SDL interface mapped to a Java interface resolves to the class annotated with @GraphQLType(interface)"() {
        given:
        startContext(INTERFACE_SCHEMA, INTERFACE_IMPLEMENTATION)

        when:
        def result = executeQuery("{ shelf { __typename title } }")

        then:
        result.errors.isEmpty()
        result.data.shelf == [[__typename: 'Poem', title: 'Ozymandias'], [__typename: 'Letter', title: 'To a friend']]
    }

    void "a field declares the implementation and the dictionary maps the GraphQL type to its interface"() {
        given:
        startContext(BOOK_SCHEMA, FIELD_IMPLEMENTATION)

        when:
        def result = executeQuery("{ book { title } }")

        then:
        result.errors.isEmpty()
        result.data.book == [title: 'Dune']
    }

    void "a field declares the interface and the dictionary maps the GraphQL type to its implementation"() {
        given:
        startContext(BOOK_SCHEMA, FIELD_INTERFACE)

        when:
        def result = executeQuery("{ book { title } }")

        then:
        result.errors.isEmpty()
        result.data.book == [title: 'Dune']
    }

    void "one field declares the interface and another its implementation for one GraphQL type"() {
        given:
        startContext(TWO_FIELDS_SCHEMA, BOTH_FIELDS)

        when:
        def result = executeQuery("{ book { title } latestBook { title } }")

        then:
        result.errors.isEmpty()
        result.data.book == [title: 'Dune']
        result.data.latestBook == [title: 'Children of Dune']
    }

    void "an interface and the class annotated with @GraphQLType(interface) mapped to two GraphQL types is an error"() {
        given:
        startContext(BOOK_OR_NOVEL_SCHEMA, ANNOTATED_IMPLEMENTATION)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof AmbiguousTypeMappingException
        e.cause.message == """The SchemaMappingDictionary maps GraphQL object types ambiguously:
  `Book` is mapped to the interface ${BookGraph.name} and `Novel` to ${BookModel.name}, which implements it
An object of the implementing class would match both GraphQL types. Map each GraphQL object type to its own class, or map only one of them to the interface."""
        e.cause.graphQlTypes == ['Book', 'Novel']
    }

    void "an interface and a class implementing it mapped to two GraphQL types is an error"() {
        given:
        startContext(BOOK_OR_NOVEL_SCHEMA, IMPLEMENTING_CLASS)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof AmbiguousTypeMappingException
        e.cause.message == """The SchemaMappingDictionary maps GraphQL object types ambiguously:
  `Book` is mapped to the interface ${BookGraph.name} and `Novel` to ${NovelModel.name}, which implements it
An object of the implementing class would match both GraphQL types. Map each GraphQL object type to its own class, or map only one of them to the interface."""
        e.cause.graphQlTypes == ['Book', 'Novel']
    }

    void "a class and its subclass mapped to two GraphQL types keep resolving by the exact class"() {
        given:
        startContext(ESSAY_SCHEMA, SUBCLASS)

        when:
        def result = executeQuery("""
{
    shortEssay: essay(long: false) { __typename ... on Essay { title } ... on LongEssay { title } }
    longEssay: essay(long: true) { __typename ... on Essay { title } ... on LongEssay { title } }
}
""")

        then:
        result.errors.isEmpty()
        result.data.shortEssay == [__typename: 'Essay', title: 'On Style']
        result.data.longEssay == [__typename: 'LongEssay', title: 'On Method']
    }

    static interface SearchResult {
    }

    static interface BookGraph extends SearchResult {
        String getTitle()
    }

    @GraphQLType(BookGraph)
    static class BookModel implements BookGraph {
        String title
    }

    @GraphQLType
    static class AuthorModel implements SearchResult {
        String name
    }

    @GraphQLType
    static class NovelModel implements BookGraph {
        String title
    }

    static interface EssayResult {
    }

    @GraphQLType
    static class EssayModel implements EssayResult {
        String title
    }

    @GraphQLType
    static class LongEssayModel extends EssayModel {
    }

    @Requires(property = 'spec.name', value = UNION_MEMBER)
    @GraphQLRootResolver
    static class UnionMemberQuery {
        SearchResult search(boolean book) {
            return book ? new BookModel(title: 'Dune') : new AuthorModel(name: 'Herbert')
        }
    }

    @Requires(property = 'spec.name', value = UNION_MEMBER)
    @Factory
    static class UnionMemberDictionary {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (dictionary) -> dictionary
                    .registerType("Book", BookGraph)
                    .registerType("Author", AuthorModel)
        }
    }

    static interface ShelfEntry {
        String getTitle()
    }

    static interface PoemGraph extends ShelfEntry {
    }

    @GraphQLType(PoemGraph)
    static class PoemModel implements PoemGraph {
        String title
    }

    @GraphQLType
    static class LetterModel implements ShelfEntry {
        String title
    }

    @Requires(property = 'spec.name', value = INTERFACE_IMPLEMENTATION)
    @GraphQLRootResolver
    static class InterfaceImplementationQuery {
        List<ShelfEntry> shelf() {
            return [new PoemModel(title: 'Ozymandias'), new LetterModel(title: 'To a friend')]
        }
    }

    @Requires(property = 'spec.name', value = INTERFACE_IMPLEMENTATION)
    @Factory
    static class InterfaceImplementationDictionary {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (dictionary) -> dictionary
                    .registerType("Poem", PoemGraph)
                    .registerType("Letter", LetterModel)
        }
    }

    @Requires(property = 'spec.name', value = FIELD_IMPLEMENTATION)
    @GraphQLRootResolver
    static class FieldImplementationQuery {
        BookModel book() {
            return new BookModel(title: 'Dune')
        }
    }

    @Requires(property = 'spec.name', value = FIELD_IMPLEMENTATION)
    @Factory
    static class FieldImplementationDictionary {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (dictionary) -> dictionary.registerType("Book", BookGraph)
        }
    }

    @Requires(property = 'spec.name', value = FIELD_INTERFACE)
    @GraphQLRootResolver
    static class FieldInterfaceQuery {
        BookGraph book() {
            return new BookModel(title: 'Dune')
        }
    }

    @Requires(property = 'spec.name', value = FIELD_INTERFACE)
    @Factory
    static class FieldInterfaceDictionary {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (dictionary) -> dictionary.registerType("Book", BookModel)
        }
    }

    @Requires(property = 'spec.name', value = BOTH_FIELDS)
    @GraphQLRootResolver
    static class BothFieldsQuery {
        BookGraph book() {
            return new BookModel(title: 'Dune')
        }

        BookModel latestBook() {
            return new BookModel(title: 'Children of Dune')
        }
    }

    @Requires(property = 'spec.name', pattern = 'DictionaryJavaInterfaceSpec\\.(annotatedImplementation|implementingClass)')
    @GraphQLRootResolver
    static class BookOrNovelQuery {
        SearchResult search(boolean book) {
            return new BookModel(title: 'Dune')
        }
    }

    @Requires(property = 'spec.name', value = ANNOTATED_IMPLEMENTATION)
    @Factory
    static class AnnotatedImplementationDictionary {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (dictionary) -> dictionary
                    .registerType("Book", BookGraph)
                    .registerType("Novel", BookModel)
        }
    }

    @Requires(property = 'spec.name', value = IMPLEMENTING_CLASS)
    @Factory
    static class ImplementingClassDictionary {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (dictionary) -> dictionary
                    .registerType("Book", BookGraph)
                    .registerType("Novel", NovelModel)
        }
    }

    @Requires(property = 'spec.name', value = SUBCLASS)
    @GraphQLRootResolver
    static class SubclassQuery {
        EssayResult essay(boolean isLong) {
            return isLong ? new LongEssayModel(title: 'On Method') : new EssayModel(title: 'On Style')
        }
    }

    @Requires(property = 'spec.name', value = SUBCLASS)
    @Factory
    static class SubclassDictionary {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (dictionary) -> dictionary
                    .registerType("Essay", EssayModel)
                    .registerType("LongEssay", LongEssayModel)
        }
    }

}
