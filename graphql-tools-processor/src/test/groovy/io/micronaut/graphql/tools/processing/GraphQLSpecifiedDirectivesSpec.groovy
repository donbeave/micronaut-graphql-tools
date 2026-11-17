package io.micronaut.graphql.tools.processing

import spock.lang.Specification

/**
 * The processor keeps a copy of the runtime module's set of built-in directive names (graphql-java 26 removed
 * {@code DirectiveInfo}); the runtime spec compares that set with graphql-java on every leg.
 */
class GraphQLSpecifiedDirectivesSpec extends Specification {

    def "the names equal the runtime module's"() {
        expect:
        GraphQLSpecifiedDirectives.NAMES == io.micronaut.graphql.tools.GraphQLSpecifiedDirectives.NAMES
    }

    def "defer is built in but not specified"() {
        expect:
        GraphQLSpecifiedDirectives.isBuiltIn("defer") && !GraphQLSpecifiedDirectives.isSpecified("defer")
        GraphQLSpecifiedDirectives.SPECIFIED == io.micronaut.graphql.tools.GraphQLSpecifiedDirectives.SPECIFIED
    }

    def "other names, a different case, an empty name and null are not specified"() {
        expect:
        !GraphQLSpecifiedDirectives.isSpecified(name)

        where:
        name << ["Include", "", "custom", "auth", null]
    }
}
