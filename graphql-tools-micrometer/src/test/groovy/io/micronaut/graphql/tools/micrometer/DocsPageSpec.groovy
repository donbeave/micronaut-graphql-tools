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
package io.micronaut.graphql.tools.micrometer

import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** The guide page names every meter, tag and configuration key of the module. */
class DocsPageSpec extends Specification {

    @Shared Path root = Paths.get(System.getProperty('docs.root'))
    @Shared String page = Files.readString(root.resolve('src/main/docs/guide/observability.adoc'))

    void "the page names the meter #name"() {
        expect:
        page.contains("`${name}`")

        where:
        name << [GraphQLMetrics.REQUEST_TIMER, GraphQLMetrics.DATA_LOADER_BATCHES, GraphQLMetrics.DATA_LOADER_BATCH_KEYS,
                 GraphQLMetrics.OPERATION_TAG, GraphQLMetrics.OUTCOME_TAG,
                 GraphQLMetrics.LOADER_TAG, GraphQLMetrics.ANONYMOUS_OPERATION,
                 GraphQLMetrics.OTHER_OPERATION]
    }

    void "the page documents the key #key"() {
        expect:
        page.contains("`${key}`")

        where:
        key << ['micronaut.metrics.binders.graphql.enabled', 'micronaut.metrics.binders.graphql.max-operations']
    }

    void "the page documents the DataLoader statistics key"() {
        expect:
        page.contains('`micronaut.graphql.tools.execution.data-loader-statistics`')
    }

}
