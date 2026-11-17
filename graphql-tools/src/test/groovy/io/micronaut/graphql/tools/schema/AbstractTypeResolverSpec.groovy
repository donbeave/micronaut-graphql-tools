package io.micronaut.graphql.tools.schema

import graphql.TypeResolutionEnvironment
import graphql.execution.TypeResolutionParameters
import graphql.execution.UnresolvedTypeException
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLUnionType
import spock.lang.Specification

import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The per-class cache of {@link AbstractTypeResolver}: consistent under concurrency, the same object type instance
 * on every hit, errors still reported, and no strong reference to the classes it has seen.
 */
class AbstractTypeResolverSpec extends Specification {

    static class Song {}

    static class Album {}

    static class LiveAlbum extends Album {}

    static class Stray {}

    static graphql.schema.GraphQLFieldDefinition field() {
        return graphql.schema.GraphQLFieldDefinition.newFieldDefinition().name("name")
                .type(graphql.Scalars.GraphQLString).build()
    }

    static final GraphQLObjectType SONG = GraphQLObjectType.newObject().name("Song").field(field()).build()
    static final GraphQLObjectType ALBUM = GraphQLObjectType.newObject().name("Album").field(field()).build()

    GraphQLSchema schema = GraphQLSchema.newSchema()
            .query(GraphQLObjectType.newObject().name("Query").field(field()).build())
            .additionalType(GraphQLUnionType.newUnionType().name("Result").possibleType(SONG).possibleType(ALBUM).build())
            .codeRegistry(GraphQLCodeRegistry.newCodeRegistry().typeResolver("Result") { env -> resolver.getType(env) }.build())
            .build()

    AtomicInteger schemaReads = new AtomicInteger()

    AbstractTypeResolver resolver = new AbstractTypeResolver({ schemaReads.incrementAndGet(); schema }, "union",
            "Result", [(Song): "Song"], [Song: Song, Album: Album])

    static TypeResolutionEnvironment env(Object object) {
        return TypeResolutionParameters.newParameters().value(object).build()
    }

    def "resolves by the exact class, by assignability and returns the cached object type of the schema"() {
        expect:
        resolver.getType(env(new Song())).is(schema.getObjectType("Song"))
        resolver.getType(env(new Album())).is(schema.getObjectType("Album"))
        resolver.getType(env(new LiveAlbum())).is(schema.getObjectType("Album"))
        resolver.getType(env(new LiveAlbum())).is(schema.getObjectType("Album"))
    }

    def "the schema is read once per class, not once per call"() {
        when:
        10.times { resolver.getType(env(new Album())) }
        int afterFirstClass = schemaReads.get()
        100.times { resolver.getType(env(new Album())) }

        then:
        schemaReads.get() == afterFirstClass
    }

    def "an unresolvable class is still reported, on every call, with the abstract type"() {
        when:
        resolver.getType(env(new Stray()))

        then:
        UnresolvedTypeException e = thrown()
        e.message.contains("The object of class " + Stray.name + " is none of the object types of union Result")
        e.message.contains("Album (" + Album.name + ")")
        e.message.contains("Song (" + Song.name + ")")
        e.interfaceOrUnionType.name == "Result"

        when:
        resolver.getType(env(new Stray()))

        then:
        thrown(UnresolvedTypeException)
    }

    def "an ambiguous class is still reported"() {
        given:
        def ambiguous = new AbstractTypeResolver({ schema }, "union", "Result", [:], [Song: Object, Album: Object])

        when:
        ambiguous.getType(env(new Stray()))

        then:
        UnresolvedTypeException e = thrown()
        e.message.contains("is an instance of more than one object type of union Result")
    }

    def "concurrent resolution from several threads is consistent"() {
        given:
        int threads = 8
        def pool = Executors.newFixedThreadPool(threads)
        def start = new CountDownLatch(1)
        Object[] objects = [new Song(), new Album(), new LiveAlbum(), new Stray()] as Object[]

        when:
        def futures = (1..threads).collect { t ->
            pool.submit({
                start.await()
                List<String> seen = []
                5000.times { i ->
                    Object object = objects[(i + t) % objects.length]
                    try {
                        seen << resolver.getType(env(object)).name
                    } catch (UnresolvedTypeException ignored) {
                        seen << "error"
                    }
                }
                return seen
            } as java.util.concurrent.Callable<List<String>>)
        }
        start.countDown()
        def results = futures.collect { it.get(60, TimeUnit.SECONDS) }

        then:
        results.every { seen -> seen.every { it in ["Song", "Album", "error"] } }
        (0..<objects.length).every { idx ->
            Set<String> names = [] as Set
            results.eachWithIndex { seen, t -> seen.eachWithIndex { name, i -> if ((i + t + 1) % objects.length == idx) names << name } }
            names.size() == 1
        }

        cleanup:
        pool.shutdownNow()
    }

    def "a class of a discarded class loader is not retained by the cache"() {
        given:
        def subject = new AbstractTypeResolver({ schema }, "union", "Result", [:],
                [Song: Song, Album: DiscardableClassLoaderFixture.Album])

        when:
        def references = DiscardableClassLoaderFixture.resolveFromDiscardableLoader(subject, "Album")
        for (int i = 0; i < 100 && references.any { it.get() != null }; i++) {
            System.gc()
            Thread.sleep(50)
        }

        then:
        references.every { it.get() == null }
    }
}
