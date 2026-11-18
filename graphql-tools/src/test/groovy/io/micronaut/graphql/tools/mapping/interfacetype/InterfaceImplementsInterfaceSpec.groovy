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
package io.micronaut.graphql.tools.mapping.interfacetype

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

class InterfaceImplementsInterfaceSpec extends AbstractTest {

    static final String SPEC_NAME = "InterfaceImplementsInterfaceSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  media: [Media!]!
  node(id: ID!): Node
}

interface Node {
  id: ID!
}

interface Media implements Node {
  id: ID!
  title: String!
}

type Video implements Media & Node {
  id: ID!
  title: String!
  duration: Int!
}

type Podcast implements Media & Node {
  id: ID!
  title: String!
  host: String!
}
"""

    void "fields returning an interface and the interface it implements resolve to the object types"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    media {
        __typename
        id
        title
        ... on Video { duration }
        ... on Podcast { host }
    }
    video: node(id: "v1") {
        __typename
        id
        ... on Media { title }
    }
    podcast: node(id: "p1") {
        __typename
        id
    }
}
""")

        then:
        result.errors.isEmpty()
        result.data.media == [
                [__typename: 'Video', id: 'v1', title: 'Intro', duration: 90],
                [__typename: 'Podcast', id: 'p1', title: 'Talk', host: 'Ann']
        ]
        result.data.video == [__typename: 'Video', id: 'v1', title: 'Intro']
        result.data.podcast == [__typename: 'Podcast', id: 'p1']
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        List<Media> media() {
            return [new Video(id: 'v1', title: 'Intro', duration: 90), new Podcast(id: 'p1', title: 'Talk', host: 'Ann')]
        }

        Node node(String id) {
            return media().find { it.id == id }
        }
    }

    static interface Node {
        String getId()
    }

    static interface Media extends Node {
        String getTitle()
    }

    @GraphQLType
    static class Video implements Media {
        String id
        String title
        Integer duration
    }

    @GraphQLType
    static class Podcast implements Media {
        String id
        String title
        String host
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Factory
    static class DictionaryFactory {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (schemaMappingDictionary) -> schemaMappingDictionary
                    .registerType("Video", Video.class)
                    .registerType("Podcast", Podcast.class)
        }
    }

}
