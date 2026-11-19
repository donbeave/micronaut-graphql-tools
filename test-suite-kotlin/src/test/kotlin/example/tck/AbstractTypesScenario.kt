/*
 * Copyright 2021-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package example.tck

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.mapping.SchemaMappingDictionary
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
