package io.micronaut.graphql.tools.wiring

import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaParser
import io.micronaut.graphql.tools.RuntimeWiringCustomizer
import spock.lang.Specification

import java.lang.reflect.Constructor
import java.lang.reflect.Method

class CustomizerSnapshotSpec extends Specification {

    private static final Class<?> CUSTOMIZERS = Class.forName('io.micronaut.graphql.tools.RuntimeWiringCustomizers')
    private static final Class<?> DIRECTIVE_WIRINGS = Class.forName('io.micronaut.graphql.tools.SchemaDirectiveWirings')

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
