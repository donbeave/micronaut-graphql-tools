package io.micronaut.graphql.tools

import spock.lang.Specification

/**
 * The options of the compile-time check live under {@code micronaut.graphql.tools.processor.}, a segment that no runtime key
 * uses: Micronaut copies the options of a compilation into the system properties, and the visitor rejects an
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
