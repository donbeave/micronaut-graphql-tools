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
package io.micronaut.graphql.tools

import spock.lang.Specification

/**
 * The options of the compile-time check live under {@code micronaut.graphql.tools.processor.}, a segment that no runtime key
 * uses (D-39, DD-5): Micronaut copies the options of a compilation into the system properties, and the visitor rejects an
 * unknown key under that segment only because a runtime key can never be one.
 */
class ProcessorOptionNamespaceSpec extends Specification {

    def "the configuration has no key under the processor segment"() {
        given:
        def metadata = Thread.currentThread().contextClassLoader.getResources('META-INF/spring-configuration-metadata.json')
                .toList().collect { it.text }.join('\n')
        def names = (metadata =~ /"name":"(micronaut\.graphql\.tools[^"]*)"/).collect { it[1] }

        expect:
        names.size() >= 12
        names.findAll { it.startsWith(GraphQLToolsConfiguration.PREFIX + '.processor') } == []
    }
}
