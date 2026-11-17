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

    AbstractTypeResolver resolver = new AbstractTypeResolver("union",
            "Result", [(Song): "Song"], [Song: Song, Album: Album])

    TypeResolutionEnvironment env(Object object) {
        return envOf(object, schema)
    }

    static TypeResolutionEnvironment envOf(Object object, GraphQLSchema schema) {
        return TypeResolutionParameters.newParameters().value(object).schema(schema).build()
    }

    def "resolves by the exact class, by assignability and returns the cached object type of the schema"() {
        expect:
        resolver.getType(env(new Song())).is(schema.getObjectType("Song"))
        resolver.getType(env(new Album())).is(schema.getObjectType("Album"))
        resolver.getType(env(new LiveAlbum())).is(schema.getObjectType("Album"))
        resolver.getType(env(new LiveAlbum())).is(schema.getObjectType("Album"))
    }

    def "the object type comes from the schema of the execution, also when another component replaced the schema"() {
        given:
        def replacedSong = GraphQLObjectType.newObject().name("Song").field(field()).build()
        def replacedAlbum = GraphQLObjectType.newObject().name("Album").field(field()).build()
        GraphQLSchema replaced = GraphQLSchema.newSchema()
                .query(GraphQLObjectType.newObject().name("Query").field(field()).build())
                .additionalType(GraphQLUnionType.newUnionType().name("Result").possibleType(replacedSong)
                        .possibleType(replacedAlbum).build())
                .codeRegistry(GraphQLCodeRegistry.newCodeRegistry().typeResolver("Result") { env -> resolver.getType(env) }.build())
                .build()

        expect: "the same resolver answers for either schema, whichever it saw first"
        resolver.getType(envOf(new Song(), schema)).is(schema.getObjectType("Song"))
        resolver.getType(envOf(new Song(), replaced)).is(replaced.getObjectType("Song"))
        resolver.getType(envOf(new LiveAlbum(), replaced)).is(replaced.getObjectType("Album"))
        resolver.getType(envOf(new LiveAlbum(), schema)).is(schema.getObjectType("Album"))
        !replaced.getObjectType("Song").is(schema.getObjectType("Song"))
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
        def ambiguous = new AbstractTypeResolver("union", "Result", [:], [Song: Object, Album: Object])

        when:
        ambiguous.getType(env(new Stray()))

        then:
        UnresolvedTypeException e = thrown()
        e.message.contains("is an instance of more than one object type of union Result")
    }

    def "an object that is an instance of several candidates resolves to the most specific, in any mapping order"() {
        given:
        def mappings = [Song: MostSpecificFixture.Base, Album: MostSpecificFixture.Entity]
        def ordered = new AbstractTypeResolver("union", "Result", [:], mappings)
        def reversed = new AbstractTypeResolver("union", "Result", [:],
                new LinkedHashMap(mappings.entrySet().toList().reverse().collectEntries { [(it.key): it.value] }))

        expect:
        [ordered, reversed].every {
            it.getType(env(new MostSpecificFixture.Entity())).name == "Album" &&
                    it.getType(env(new MostSpecificFixture.Proxy())).name == "Album" &&
                    it.getType(env(new MostSpecificFixture.Base())).name == "Song"
        }
    }

    def "candidates without a single most specific one are an error naming them, and the error is not cached for other classes"() {
        given:
        def subject = new AbstractTypeResolver("union", "Result", [:],
                [Song: MostSpecificFixture.Left, Album: MostSpecificFixture.Right])

        when:
        subject.getType(env(new MostSpecificFixture.Both()))

        then:
        UnresolvedTypeException e = thrown()
        e.message.contains("is an instance of more than one object type of union Result, and none is more specific " +
                "than the others: Album (" + MostSpecificFixture.Right.name + "), Song (" + MostSpecificFixture.Left.name + ")")

        expect:
        subject.getType(env(new MostSpecificFixture.OnlyLeft())).name == "Song"
        subject.getType(env(new MostSpecificFixture.OnlyRight())).name == "Album"

        when:
        subject.getType(env(new MostSpecificFixture.Both()))

        then:
        thrown(UnresolvedTypeException)
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
        def subject = new AbstractTypeResolver("union", "Result", [:],
                [Song: Song, Album: DiscardableClassLoaderFixture.Album])

        when:
        def references = DiscardableClassLoaderFixture.resolveFromDiscardableLoader(subject, schema, "Album")
        for (int i = 0; i < 100 && references.any { it.get() != null }; i++) {
            System.gc()
            Thread.sleep(50)
        }

        then:
        references.every { it.get() == null }
    }
}
