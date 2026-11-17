package example.tck

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.SchemaMappingDictionary
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import jakarta.inject.Singleton

// The fixtures of the TCK scenario `abstract-types`: an interface and a union of the same classes.

interface Media {
    val title: String
    val seconds: Int
}

interface Lookup

@GraphQLType
class Song(override val title: String, override val seconds: Int, val artist: String) : Media, Lookup

@GraphQLType
class Podcast(override val title: String, override val seconds: Int, val host: String) : Media, Lookup

@GraphQLType
class Missing(val reason: String) : Lookup

@Singleton
@Requires(property = "tck.scenario", value = "abstract-types")
class AbstractTypesMappings : TckTypeMappings {
    override fun register(dictionary: SchemaMappingDictionary) {
        dictionary
            .registerType("Song", Song::class.java)
            .registerType("Podcast", Podcast::class.java)
            .registerType("Missing", Missing::class.java)
    }
}

@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "abstract-types")
class AbstractTypesQueries {

    private val library: List<Media> = listOf(
        Song("Aria", 245, "Ada"),
        Podcast("Weekly", 1800, "Grace"),
        Song("Nocturne", 180, "Lin"),
    )

    fun library(): List<Media> = library

    fun find(title: String): Lookup =
        library.firstOrNull { it.title == title } as Lookup? ?: Missing("no media titled $title")
}
