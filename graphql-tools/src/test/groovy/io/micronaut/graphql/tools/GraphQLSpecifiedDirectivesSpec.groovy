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

import graphql.Directives
import spock.lang.Specification
import io.micronaut.graphql.tools.internal.wiring.GraphQLSpecifiedDirectives
import io.micronaut.graphql.tools.internal.GraphQLUtils

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
        graphqlJavaNames().every { GraphQLSpecifiedDirectives.isBuiltIn(it) }
    }

    def "the specified names are listed by graphql-java on this leg, and defer is only built in"() {
        expect:
        GraphQLSpecifiedDirectives.SPECIFIED.every { graphqlJavaNames().contains(it) }
        GraphQLSpecifiedDirectives.isBuiltIn("defer") && !GraphQLSpecifiedDirectives.isSpecified("defer")
        GraphQLSpecifiedDirectives.isBuiltIn("experimental_disableErrorPropagation") &&
                !GraphQLSpecifiedDirectives.isSpecified("experimental_disableErrorPropagation")
    }

    def "the set equals graphql-java's on 25 and later, and adds only the two graphql-java 25 introduced before"() {
        given:
        boolean modern = Directives.metaClass.respondsTo(Directives, "isBuiltInDirective", String) ||
                hasDirectiveField("ExperimentalDisableErrorPropagationDirective")

        expect:
        modern ? graphqlJavaNames() == GraphQLSpecifiedDirectives.NAMES :
                (GraphQLSpecifiedDirectives.NAMES - graphqlJavaNames()) == ["defer", "experimental_disableErrorPropagation"] as Set
    }

    def "an input type used only by a declared defer directive is still found as a directive argument type"() {
        given:
        def registry = new graphql.schema.idl.SchemaParser().parse(
                "directive @defer(options: DeferOptions) on FIELD_DEFINITION\ninput DeferOptions { a: String }\ntype Query { a: String }")

        expect:
        GraphQLUtils.findDirectiveArgumentTypes(registry).contains("DeferOptions")
    }

    def "the provided names are the specified ones plus what a new registry of this leg registers"() {
        given:
        def registered = new graphql.schema.idl.TypeDefinitionRegistry().directiveDefinitions.keySet()

        expect:
        GraphQLSpecifiedDirectives.SPECIFIED.every { GraphQLSpecifiedDirectives.isProvided(it) }
        registered.every { GraphQLSpecifiedDirectives.isProvided(it) }
        GraphQLSpecifiedDirectives.isProvided("defer") == registered.contains("defer")
    }

    def "other names, a different case, an empty name and null are not specified"() {
        expect:
        !GraphQLSpecifiedDirectives.isSpecified(name)
        !GraphQLSpecifiedDirectives.isBuiltIn(name)

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
