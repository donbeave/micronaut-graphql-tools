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

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.mapping.SchemaMappingDictionary
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import jakarta.inject.Singleton

// The fixtures of the TCK scenario `abstract-types`: an interface and a union of the same classes.

interface Media {
    String getTitle()

    int getSeconds()
}

interface Lookup {
}

@CompileStatic
@GraphQLType
class Song implements Media, Lookup {
    final String title
    final int seconds
    final String artist

    Song(String title, int seconds, String artist) {
        this.title = title
        this.seconds = seconds
        this.artist = artist
    }
}

@CompileStatic
@GraphQLType
class Podcast implements Media, Lookup {
    final String title
    final int seconds
    final String host

    Podcast(String title, int seconds, String host) {
        this.title = title
        this.seconds = seconds
        this.host = host
    }
}

@CompileStatic
@GraphQLType
class Missing implements Lookup {
    final String reason

    Missing(String reason) {
        this.reason = reason
    }
}

@CompileStatic
@Singleton
@Requires(property = 'tck.scenario', value = 'abstract-types')
class AbstractTypesMappings implements TckTypeMappings {
    @Override
    void register(SchemaMappingDictionary dictionary) {
        dictionary
                .registerType('Song', Song)
                .registerType('Podcast', Podcast)
                .registerType('Missing', Missing)
    }
}

@CompileStatic
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'abstract-types')
class AbstractTypesQueries {

    private final List<Media> library = [
            new Song('Aria', 245, 'Ada'),
            new Podcast('Weekly', 1800, 'Grace'),
            new Song('Nocturne', 180, 'Lin'),
    ] as List<Media>

    List<Media> library() {
        return library
    }

    Lookup find(String title) {
        Media found = library.find { it.title == title }
        return found != null ? (Lookup) found : new Missing("no media titled $title".toString())
    }
}
