package io.micronaut.graphql.tools.mapping.extension

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.BindingManifest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import org.intellij.lang.annotations.Language

/**
 * A field of a type extension ({@code extend type}) is bound and checked like a field of the type itself: by a
 * property, a type resolver or a root resolver, with the same errors, and with its line in the manifest. It is never
 * left to graphql-java's default data fetcher.
 */
class TypeExtensionsSpec extends AbstractTest {

    static final String SPEC_NAME = "TypeExtensionsSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  book: Book
}

type Book {
  title: String
}

extend type Query {
  shelf: String
}

extend type Book {
  pages: Int
  author: String
}
"""

    void "the fields of extensions are bound by a property, a type resolver and a root resolver, and listed in the manifest"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery('{ shelf book { title pages author } }')
        def manifest = applicationContext.getBean(BindingManifest)

        then:
        result.errors.isEmpty()
        result.data == [shelf: 'shelf', book: [title: 'title', pages: 120, author: 'resolved author']]
        manifest.lines.containsAll([
                'Book.pages: Int → property · io.micronaut.graphql.tools.mapping.extension.TypeExtensionsSpec$Book#getPages() · java.lang.Integer · java.lang.Integer',
                'Book.author: String → resolver · io.micronaut.graphql.tools.mapping.extension.TypeExtensionsSpec$BookResolver#author(io.micronaut.graphql.tools.mapping.extension.TypeExtensionsSpec$Book) · java.lang.String · java.lang.String',
                'Query.shelf: String → root · io.micronaut.graphql.tools.mapping.extension.TypeExtensionsSpec$Query#shelf() · java.lang.String · java.lang.String',
        ])
    }

    void "an extension field of the wrong Java type fails the startup, as any field does"() {
        given:
        startContext(SCHEMA.replace('pages: Int', 'pages: Boolean'), SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause.message.contains('GraphQL coordinate: Book.pages')
    }

    void "an extension field nothing binds fails the startup, as any field does"() {
        given:
        startContext(SCHEMA.replace('  author: String\n', '  author: String\n  isbn: String\n'), SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause.message.contains('GraphQL coordinate: Book.isbn')
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        Book book() {
            return new Book()
        }

        String shelf() {
            return 'shelf'
        }
    }

    @GraphQLType
    static class Book {
        String getTitle() {
            return 'title'
        }

        Integer getPages() {
            return 120
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLTypeResolver(Book)
    static class BookResolver {
        String author(Book book) {
            return 'resolved author'
        }
    }

}
