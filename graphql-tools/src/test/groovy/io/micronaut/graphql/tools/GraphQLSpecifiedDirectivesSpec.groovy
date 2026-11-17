package io.micronaut.graphql.tools

import graphql.Directives
import spock.lang.Specification

/**
 * The library keeps its own set of built-in directive names, because {@code DirectiveInfo} is gone in graphql-java 26 and
 * {@code Directives.isBuiltInDirective} does not exist before it. This runs on every graphql-java leg and compares the
 * set with whatever that line offers.
 */
class GraphQLSpecifiedDirectivesSpec extends Specification {

    def "the names are the specified directives and the two graphql-java adds itself"() {
        expect:
        GraphQLSpecifiedDirectives.NAMES == ["include", "skip", "deprecated", "specifiedBy", "oneOf", "defer",
                                             "experimental_disableErrorPropagation"] as Set
    }

    def "every directive that graphql-java reports as built in or specified on this leg is specified"() {
        expect:
        !graphqlJavaNames().isEmpty()
        graphqlJavaNames().every { GraphQLSpecifiedDirectives.isSpecified(it) }
    }

    def "the set equals graphql-java's on 25 and later, and adds only the two graphql-java 25 introduced before"() {
        given:
        boolean modern = Directives.metaClass.respondsTo(Directives, "isBuiltInDirective", String) ||
                hasDirectiveField("ExperimentalDisableErrorPropagationDirective")

        expect:
        modern ? graphqlJavaNames() == GraphQLSpecifiedDirectives.NAMES :
                (GraphQLSpecifiedDirectives.NAMES - graphqlJavaNames()) == ["defer", "experimental_disableErrorPropagation"] as Set
    }

    def "other names, a different case, an empty name and null are not specified"() {
        expect:
        !GraphQLSpecifiedDirectives.isSpecified(name)

        where:
        name << ["Include", "", "custom", "auth", "stream", null]
    }

    // 26.x: Directives.BUILT_IN_DIRECTIVES; 25.x and before: DirectiveInfo.GRAPHQL_SPECIFICATION_DIRECTIVES
    private static Set<String> graphqlJavaNames() {
        try {
            return (Directives.BUILT_IN_DIRECTIVES as Collection).collect { it.name } as Set
        } catch (MissingPropertyException ignored) {
            def info = Class.forName("graphql.schema.idl.DirectiveInfo")
            return (info.getField("GRAPHQL_SPECIFICATION_DIRECTIVES").get(null) as Collection).collect { it.name } as Set
        }
    }

    private static boolean hasDirectiveField(String name) {
        try {
            Directives.getField(name)
            return true
        } catch (NoSuchFieldException ignored) {
            return false
        }
    }
}
