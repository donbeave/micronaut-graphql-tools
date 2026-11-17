package io.micronaut.graphql.tools.manifest

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import io.micronaut.aop.Around
import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.BindingManifest
import io.micronaut.graphql.tools.BindingManifestRecorder
import io.micronaut.graphql.tools.BindingManifestWriter
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLField
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicInteger

/**
 * The binding manifest: one line per schema coordinate, saying which Java member serves it.
 */
class BindingManifestSpec extends AbstractTest {

    static final String SPEC_NAME = "BindingManifestSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
  mutation: Mutation
  subscription: Subscription
}

directive @computed on FIELD_DEFINITION

scalar Long

type Query {
  item(id: ID!): Item
  shelf(filter: ShelfFilter, first: Int): Shelf!
  search(text: String!): [SearchHit!]!
}

type Mutation {
  rename(id: ID!, name: String!): Item
}

type Subscription {
  priceChanges(id: ID!): Long!
}

type Item implements Node {
  id: ID!
  name: String!
  price: Long! @computed
  label(upper: Boolean): String
  state: ItemState!
}

interface Node {
  id: ID!
}

type Shelf {
  items: [Node!]!
  note: String
}

type Person {
  name: String!
}

union SearchHit = Item | Person

enum ItemState {
  NEW
  SOLD
}

input ShelfFilter {
  state: ItemState
  minPrice: Long
}

type Legacy {
  code: String
}
"""

    static final Map CONFIG = [
            'micronaut.graphql.tools.resolver-directives': ['computed'],
            'micronaut.graphql.tools.allow-unreachable'  : ['Legacy']
    ]

    void "the manifest names the member, the declared and the converted type of every coordinate"() {
        given:
        startContext(SCHEMA, SPEC_NAME, CONFIG)

        when:
        BindingManifest manifest = applicationContext.getBean(BindingManifest)

        then:
        manifest.lines.join('\n') + '\n' == golden('binding-manifest.txt')
    }

    void "the header names the format, the binder, the graphql-java version and the schema hash"() {
        given:
        startContext(SCHEMA, SPEC_NAME, CONFIG)

        when:
        BindingManifest manifest = applicationContext.getBean(BindingManifest)

        then:
        manifest.header.size() == 4
        manifest.header[0] == '# graphql-binding-manifest 1'
        manifest.header[1] == '# binder: micronaut-graphql-tools unknown'
        manifest.header[2] ==~ /# graphql-java: \d+\.\d+(\.\d+)?/
        manifest.header[3] ==~ /# schema-sha256: [0-9a-f]{64}/
        manifest.render() == manifest.header.join('\n') + '\n' + manifest.lines.join('\n') + '\n'
        manifest.lines == manifest.lines.toSorted()
    }

    void "the schema hash does not depend on the order of the definitions"() {
        given:
        startContext(SCHEMA, SPEC_NAME, CONFIG)
        String first = applicationContext.getBean(BindingManifest).header[3]
        applicationContext.close()

        when:
        startContext(SCHEMA.replace('enum ItemState {\n  NEW\n  SOLD\n}\n', '') + '\nenum ItemState {\n  NEW\n  SOLD\n}\n',
                SPEC_NAME, CONFIG)

        then:
        applicationContext.getBean(BindingManifest).header[3] == first
    }

    static String golden(String name) {
        new String(BindingManifestSpec.getResourceAsStream('/manifest/' + name).readAllBytes(), StandardCharsets.UTF_8)
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        Item item(String id) {
            return new Item(id: id, name: 'pen')
        }

        CompletionStage<Shelf> shelf(ShelfFilter filter, Integer first) {
            return CompletableFuture.completedFuture(new Shelf())
        }

        List<SearchHit> search(String text, DataFetchingEnvironment env) {
            return []
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Mutation {
        Optional<Item> rename(String id, String name) {
            return Optional.empty()
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Subscription {
        Publisher<Long> priceChanges(String id) {
            return Flux.just(1L)
        }
    }

    static interface Node {
        String getId()
    }

    static interface SearchHit {
    }

    @GraphQLType
    static class Item implements Node, SearchHit {
        String id
        String name
        Long price
        ItemState state = ItemState.NEW

        @GraphQLField
        String label(Boolean upper) {
            return upper ? name.toUpperCase() : name
        }
    }

    @GraphQLType
    static class Person implements SearchHit {
        String name
    }

    @GraphQLType
    static class Shelf {
        List<DataFetcherResult<Node>> items = []
        String note
    }

    static enum ItemState {
        NEW, SOLD
    }

    @GraphQLInput
    static class ShelfFilter {
        ItemState state
        Long minPrice
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLTypeResolver(Item.class)
    static class ItemResolver {
        CompletionStage<Long> price(Item item) {
            return CompletableFuture.completedFuture(3L)
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Factory
    static class DictionaryFactory {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (schemaMappingDictionary) -> schemaMappingDictionary
                    .registerType("Item", Item.class)
                    .registerType("Person", Person.class)
        }
    }

    void "the line of a field that a getter and a marked type resolver can both bind says resolver"() {
        given:
        startContext(SCHEMA, SPEC_NAME, CONFIG)

        when:
        BindingManifest manifest = applicationContext.getBean(BindingManifest)

        then:
        manifest.lines.contains(("Item.price: Long! → resolver · ${ItemResolver.name}#price(${Item.name}) · " +
                "java.util.concurrent.CompletionStage<java.lang.Long> · java.lang.Long").toString())
        manifest.diagnostics == ['!unreachable Legacy']
    }

    void "with precedence property-first the line says property and an ambiguous line names both members"() {
        given:
        startContext(SCHEMA, SPEC_NAME, [
                'micronaut.graphql.tools.precedence'       : 'property-first',
                'micronaut.graphql.tools.allow-unreachable': ['Legacy'],
                'micronaut.graphql.tools.inert-directives' : ['computed']
        ])

        when:
        BindingManifest manifest = applicationContext.getBean(BindingManifest)

        then:
        manifest.lines.contains("Item.price: Long! → property · ${Item.name}#getPrice() · java.lang.Long · java.lang.Long".toString())
        manifest.diagnostics == [
                "!ambiguous Item.price · ${Item.name}#getPrice() | ${ItemResolver.name}#price(${Item.name})".toString(),
                '!unreachable Legacy'
        ]
    }

    // --- AOP proxies ---

    void "a method of an intercepted resolver is named on the class the user wrote, not on the proxy"() {
        given:
        startContext("""
schema {
  query: Query
}

type Query {
  book: Book
}

type Book {
  title: String!
  summary: String!
}
""", SPEC_NAME + ".proxy")

        when:
        BindingManifest manifest = applicationContext.getBean(BindingManifest)

        then:
        executeQuery('{ book { summary } }').data == [book: [summary: 'Summary of Dune']]
        TracedInterceptor.CALLS.get() == 2
        manifest.lines.findAll { it.startsWith('Query.book') || it.startsWith('Book.summary') } == [
                "Book.summary: String! → resolver · ${TracedBookResolver.name}#summary(${Book.name}) · java.lang.String · java.lang.String".toString(),
                "Query.book: Book → root · ${TracedBookQuery.name}#book() · ${Book.name} · ${Book.name}".toString()
        ]
    }

    @GraphQLRootResolver
    static interface BookQueryContract {
        Book book()
    }

    @GraphQLTypeResolver(Book.class)
    static interface BookResolverContract {
        String summary(Book book)
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".proxy")
    @Singleton
    static class TracedBookQuery implements BookQueryContract {
        @Traced
        @Override
        Book book() {
            return new Book(title: 'Dune')
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".proxy")
    @Singleton
    static class TracedBookResolver implements BookResolverContract {
        @Traced
        @Override
        String summary(Book book) {
            return 'Summary of ' + book.title
        }
    }

    @GraphQLType
    static class Book {
        String title
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target([ElementType.METHOD, ElementType.TYPE])
    @Around
    static @interface Traced {
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".proxy")
    @Singleton
    @InterceptorBean(Traced)
    static class TracedInterceptor implements MethodInterceptor<Object, Object> {

        static final AtomicInteger CALLS = new AtomicInteger()

        @Override
        Object intercept(MethodInvocationContext<Object, Object> context) {
            CALLS.incrementAndGet()
            return context.proceed()
        }
    }

    // --- writing at startup ---

    void "with manifest.enabled the manifest is written to manifest.path at startup"() {
        given:
        Path path = Files.createTempDirectory('manifest').resolve('out/binding-manifest.txt')

        when:
        startContext(SCHEMA, SPEC_NAME, CONFIG + [
                'micronaut.graphql.tools.manifest.enabled': true,
                'micronaut.graphql.tools.manifest.path'   : path.toString()
        ])

        then: 'the file exists before anything asked for the GraphQL bean'
        Files.readString(path, StandardCharsets.UTF_8) == applicationContext.getBean(BindingManifest).render()
        !Files.readString(path, StandardCharsets.UTF_8).contains('\r')

        cleanup:
        path.toFile().delete()
    }

    void "with manifest.enabled and no path the manifest is logged"() {
        given:
        ListAppender<ILoggingEvent> appender = new ListAppender<>()
        appender.start()
        Logger logger = (Logger) LoggerFactory.getLogger(BindingManifestWriter)
        logger.addAppender(appender)

        when:
        startContext(SCHEMA, SPEC_NAME, CONFIG + ['micronaut.graphql.tools.manifest.enabled': true])

        then:
        appender.list*.formattedMessage == ['GraphQL binding manifest:\n' + applicationContext.getBean(BindingManifest).render()]

        cleanup:
        logger.detachAppender(appender)
    }

    void "by default nothing is written and the schema is bound only when the GraphQL bean is needed"() {
        when:
        startContext(SCHEMA, SPEC_NAME, CONFIG)

        then:
        !applicationContext.containsBean(BindingManifestWriter)
        applicationContext.getBean(BindingManifest).lines.size() == 36
    }

    void "a part that contains a separator cannot be written"() {
        when:
        new BindingManifestRecorder().diagnostic('unreachable', 'a · b')

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot write the binding manifest: "a · b" contains a separator ("→" or "·") or a line break.'
    }

    // --- types that only directive definitions use ---

    void "enum, input and scalar types that only directive definitions use are not unreachable"() {
        given:
        startContext("""
schema {
  query: Query
}

scalar Long

directive @restricted(level: Level!, scope: Scope, since: Long) on FIELD_DEFINITION

enum Level {
  LOW
  HIGH
}

input Scope {
  name: String
  tier: Tier
}

enum Tier {
  BASIC
}

enum Unused {
  NONE
}

type Query {
  label: String! @restricted(level: HIGH)
}
""", SPEC_NAME + ".directives", ['micronaut.graphql.tools.allow-unreachable': ['Unused'],
                'micronaut.graphql.tools.inert-directives': ['restricted']])

        when:
        BindingManifest manifest = applicationContext.getBean(BindingManifest)

        then:
        executeQuery('{ label }').data == [label: 'label']
        manifest.diagnostics == ['!unreachable Unused']
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".directives")
    @GraphQLRootResolver
    static class LabelQuery {
        String label() {
            return 'label'
        }
    }

    // --- generic types the compile-time metadata does not keep ---

    void "declared types are rendered from the reflected method, with wildcards"() {
        given:
        startContext("""
schema {
  query: Query
}

type Query {
  books: [Book!]!
}

type Book {
  title: String!
  label(upper: Boolean): String
}
""", SPEC_NAME + ".reflected")

        when:
        BindingManifest manifest = applicationContext.getBean(BindingManifest)

        then:
        manifest.lines.findAll { it.startsWith('Query.books') || it.startsWith('Book.label') } == [
                "Book.label(upper: Boolean): String → property · ${LabelledBase.name}#label(java.lang.Boolean) · java.lang.String · java.lang.String".toString(),
                "Query.books: [Book!]! → root · ${WildcardQuery.name}#books() · java.util.concurrent.CompletionStage<java.util.List<? extends ${LabelledBook.name}>> · java.util.List<? extends ${LabelledBook.name}>".toString()
        ]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".reflected")
    @GraphQLRootResolver
    static class WildcardQuery {
        CompletionStage<List<? extends LabelledBook>> books() {
            return CompletableFuture.completedFuture([new LabelledBook(title: 'Dune')])
        }
    }

    static class LabelledBase {
        @GraphQLField
        String label(Boolean upper) {
            return 'label'
        }
    }

    @GraphQLType
    static class LabelledBook extends LabelledBase {
        String title
    }

}
