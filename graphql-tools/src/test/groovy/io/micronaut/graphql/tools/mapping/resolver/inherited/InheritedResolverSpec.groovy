package io.micronaut.graphql.tools.mapping.resolver.inherited

import io.micronaut.aop.Around
import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.GraphQLResolversRegistry
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.exceptions.AmbiguousTypeResolverException
import io.micronaut.graphql.tools.exceptions.MultipleMethodsFoundException
import io.micronaut.graphql.tools.exceptions.TypeResolverContractsNotFoundException
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import java.util.concurrent.atomic.AtomicInteger

/**
 * {@link GraphQLRootResolver} and {@link GraphQLTypeResolver} are {@link java.lang.annotation.Inherited}: a bean
 * that implements an annotated interface (for example a generated resolver contract) is a resolver without an
 * annotation of its own. Side effect: a bean that extends an annotated class is a resolver too.
 */
class InheritedResolverSpec extends AbstractTest {

    static final String SPEC_NAME = "InheritedResolverSpec"

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
  title: String!
  summary: String!
}
"""

    void "a bean implementing annotated resolver interfaces binds without an annotation of its own"() {
        given:
        CountedInterceptor.CALLS.set(0)
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    book(id: "1") {
        id
        title
        summary
    }
}
""")

        then:
        result.errors.isEmpty()
        result.data.book == [id: '1', title: 'Title 1', summary: 'Summary of Title 1']

        and: 'the @Around interceptor on the implementation still runs'
        CountedInterceptor.CALLS.get() == 2
    }

    @GraphQLRootResolver
    static interface BookQueryContract {
        Book book(String id)
    }

    @GraphQLTypeResolver(Book.class)
    static interface BookResolverContract {
        String summary(Book book)
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    static class BookQuery implements BookQueryContract {
        @Counted
        @Override
        Book book(String id) {
            return new Book(id: id, title: "Title " + id)
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    static class BookResolver implements BookResolverContract {
        @Counted
        @Override
        String summary(Book book) {
            return "Summary of " + book.title
        }
    }

    @GraphQLType
    static class Book {
        String id
        String title
    }

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '.*')
    @Singleton
    @InterceptorBean(Counted)
    static class CountedInterceptor implements MethodInterceptor<Object, Object> {

        static final AtomicInteger CALLS = new AtomicInteger()

        @Override
        Object intercept(MethodInvocationContext<Object, Object> context) {
            CALLS.incrementAndGet()
            return context.proceed()
        }
    }

    // --- side effect: a bean that extends an annotated class is a resolver too ---

    static final String SUBCLASS_SPEC_NAME = "InheritedResolverSpec.subclass"

    void "a @Singleton subclass of an annotated root resolver class is registered as a root resolver"() {
        given:
        startContext("""
schema {
  query: Query
}

type Query {
  greeting: String
}
""", SUBCLASS_SPEC_NAME, ['micronaut.graphql.tools.strict': false])

        when:
        def result = executeQuery("{ greeting }")

        then:
        result.errors.isEmpty()
        result.data.greeting == 'hello from the subclass'
    }

    void "an annotated root resolver class and its @Singleton subclass binding the same field is an error"() {
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
  title: String!
}
""", SUBCLASS_SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof MultipleMethodsFoundException
        e.cause.message == """Found multiple methods for one GraphQL field.
  GraphQL coordinate: Query.book (line 7, column 3)
  GraphQL object type: Query
  GraphQL field: book
  Methods:\u0020
  1) ${AnnotatedQuery.name} book(java.lang.String id)
  2) ${SubclassQuery.name} book(java.lang.String id)"""
    }

    void "an annotated type resolver class and its @Singleton subclass binding the same field is an error"() {
        given:
        startContext(SCHEMA, SUBCLASS_SPEC_NAME + ".type")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof MultipleMethodsFoundException
        e.cause.message == """Found multiple methods for one GraphQL field.
  GraphQL coordinate: Book.summary (line 13, column 3)
  GraphQL object type: Book
  GraphQL field: summary
  Methods:\u0020
  1) ${AnnotatedBookResolver.name} summary(${Book.name} book)
  2) ${SubclassBookResolver.name} summary(${Book.name} book)"""
    }

    @Requires(property = 'spec.name', value = SUBCLASS_SPEC_NAME)
    @GraphQLRootResolver
    static class AnnotatedQuery {
        Book book(String id) {
            return new Book(id: id, title: "Title " + id)
        }
    }

    @Requires(property = 'spec.name', value = SUBCLASS_SPEC_NAME)
    @Singleton
    static class SubclassQuery extends AnnotatedQuery {
        String greeting() {
            return 'hello from the subclass'
        }
    }

    @Requires(property = 'spec.name', value = SUBCLASS_SPEC_NAME + ".type")
    @GraphQLRootResolver
    static class TypeQuery {
        Book book(String id) {
            return new Book(id: id, title: "Title " + id)
        }
    }

    @Requires(property = 'spec.name', value = SUBCLASS_SPEC_NAME + ".type")
    @GraphQLTypeResolver(Book.class)
    static class AnnotatedBookResolver {
        String summary(Book book) {
            return "Summary of " + book.title
        }
    }

    @Requires(property = 'spec.name', value = SUBCLASS_SPEC_NAME + ".type")
    @Singleton
    static class SubclassBookResolver extends AnnotatedBookResolver {
    }


    // --- one bean implementing several type resolver contracts ---

    static final String CONTRACTS_SPEC_NAME = "InheritedResolverSpec.contracts"

    @Language("GraphQL")
    static final String CONTRACTS_SCHEMA = """
schema {
  query: Query
}

type Query {
  book: Novel
}

type Novel {
  title: String
  author: Writer
  bookExtra: String
}

type Writer {
  name: String
  authorExtra: String
}
"""

    void "a bean implementing two type resolver contracts binds each method for the class of its contract"() {
        given:
        startContext(CONTRACTS_SCHEMA, CONTRACTS_SPEC_NAME)

        when:
        def result = executeQuery("{ book { title bookExtra author { name authorExtra } } }")

        then:
        result.errors.isEmpty()
        result.data.book == [title: 'T', bookExtra: 'b-T', author: [name: 'N', authorExtra: 'a-N']]
    }

    void "a method of a bean with several type resolver contracts whose source parameter matches none of them fails the startup"() {
        given:
        startContext(CONTRACTS_SCHEMA, CONTRACTS_SPEC_NAME + ".unassignable")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof AmbiguousTypeResolverException
        e.cause.message == "The bean ${UnassignableResolvers.name} is a @GraphQLTypeResolver for several classes " +
                "through ${NovelContract.name} (${Novel.name}) and ${WriterContract.name} (${Writer.name}), and the " +
                "first parameter of these methods is none of those classes, so they cannot be assigned to one: " +
                "describe(java.lang.String text). Make them non-public or give them the source parameter of " +
                "their contract."
    }

    @GraphQLType
    static class Novel {
        String title
        Writer author
    }

    @GraphQLType
    static class Writer {
        String name
    }

    @Requires(property = 'spec.name', pattern = CONTRACTS_SPEC_NAME + '.*')
    @GraphQLRootResolver
    static class ContractsQuery {
        Novel book() {
            return new Novel(title: 'T', author: new Writer(name: 'N'))
        }
    }

    @GraphQLTypeResolver(Novel.class)
    static interface NovelContract {
        String bookExtra(Novel novel)
    }

    @GraphQLTypeResolver(Writer.class)
    static interface WriterContract {
        String authorExtra(Writer writer)
    }

    @Requires(property = 'spec.name', value = CONTRACTS_SPEC_NAME)
    @Singleton
    static class BothResolvers implements NovelContract, WriterContract {
        @Override
        String bookExtra(Novel novel) {
            return 'b-' + novel.title
        }

        @Override
        String authorExtra(Writer writer) {
            return 'a-' + writer.name
        }
    }

    @Requires(property = 'spec.name', value = CONTRACTS_SPEC_NAME + ".unassignable")
    @Singleton
    static class UnassignableResolvers implements NovelContract, WriterContract {
        @Override
        String bookExtra(Novel novel) {
            return 'b-' + novel.title
        }

        @Override
        String authorExtra(Writer writer) {
            return 'a-' + writer.name
        }

        String describe(String text) {
            return text
        }
    }


    // --- the contract types cannot be read (class annotations unavailable to reflection) ---

    void "a bean that is a type resolver only through inheritance fails when its contract types cannot be read"() {
        given:
        startContext(CONTRACTS_SCHEMA, CONTRACTS_SPEC_NAME)
        applicationContext.getBean(GraphQLResolversRegistry).setTypeResolverContractReader { Class<?> type -> [:] }

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof TypeResolverContractsNotFoundException
        e.cause.message == "The bean ${BothResolvers.name} is a @GraphQLTypeResolver only through an inherited " +
                "annotation, but no type of its class hierarchy declares the annotation at runtime, so its contracts " +
                "cannot be read. Make the annotations of the contract types available to reflection (for example the " +
                "reflection configuration of a native image), or annotate the bean class directly."
    }

    void "a bean annotated directly binds as before when the contract types cannot be read"() {
        given:
        CountedInterceptor.CALLS.set(0)
        startContext(SCHEMA, SPEC_NAME + ".direct")
        applicationContext.getBean(GraphQLResolversRegistry).setTypeResolverContractReader { Class<?> type -> [:] }

        when:
        def result = executeQuery('{ book(id: "3") { title summary } }')

        then:
        result.errors.isEmpty()
        result.data.book == [title: 'Title 3', summary: 'Direct summary of Title 3']
        CountedInterceptor.CALLS.get() == 1
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".direct")
    @GraphQLRootResolver
    static class DirectQuery {
        Book book(String id) {
            return new Book(id: id, title: "Title " + id)
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".direct")
    @GraphQLTypeResolver(Book.class)
    static class DirectBookResolver {
        @Counted
        String summary(Book book) {
            return "Direct summary of " + book.title
        }
    }

}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD])
@Around
@interface Counted {
}
