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
package io.micronaut.graphql.tools.wiring

import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaParser
import io.micronaut.graphql.tools.RuntimeWiringCustomizer
import spock.lang.Specification

import java.lang.reflect.Constructor
import java.lang.reflect.Method

class CustomizerSnapshotSpec extends Specification {

    private static final Class<?> CUSTOMIZERS = Class.forName('io.micronaut.graphql.tools.internal.RuntimeWiringCustomizers')
    private static final Class<?> DIRECTIVE_WIRINGS = Class.forName('io.micronaut.graphql.tools.internal.SchemaDirectiveWirings')

    private static void apply(RuntimeWiring.Builder builder, List<RuntimeWiringCustomizer> customizers) {
        Method apply = CUSTOMIZERS.getDeclaredMethod('apply', RuntimeWiring.Builder, List, Class.forName('graphql.schema.idl.TypeDefinitionRegistry'), Map)
        apply.accessible = true
        apply.invoke(null, builder, customizers, new SchemaParser().parse('type Query { hello: String }'), [:])
    }

    private static RuntimeWiringCustomizer libraryWirings() {
        Constructor<?> constructor = DIRECTIVE_WIRINGS.getDeclaredConstructor(Collection, Collection)
        constructor.accessible = true
        constructor.newInstance([], []) as RuntimeWiringCustomizer
    }

    def "no user customizer takes no snapshot"() {
        given:
        def builder = Mock(RuntimeWiring.Builder)

        when:
        apply(builder, [libraryWirings()])

        then:
        0 * builder.build()
    }

    def "a user customizer takes the snapshots"() {
        given:
        def builder = Mock(RuntimeWiring.Builder)

        when:
        apply(builder, [libraryWirings(), { RuntimeWiring.Builder b -> } as RuntimeWiringCustomizer])

        then:
        2 * builder.build() >> RuntimeWiring.newRuntimeWiring().build()
    }

}
