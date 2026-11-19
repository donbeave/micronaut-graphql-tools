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
package io.micronaut.graphql.tools.processing

import spock.lang.Specification

/**
 * The processor keeps a copy of the runtime module's set of built-in directive names (graphql-java 26 removed
 * {@code DirectiveInfo}); the runtime spec compares that set with graphql-java on every leg.
 */
class GraphQLSpecifiedDirectivesSpec extends Specification {

    def "the names equal the runtime module's"() {
        expect:
        GraphQLSpecifiedDirectives.NAMES == io.micronaut.graphql.tools.internal.GraphQLSpecifiedDirectives.NAMES
    }

    def "defer is built in but not specified"() {
        expect:
        GraphQLSpecifiedDirectives.isBuiltIn("defer") && !GraphQLSpecifiedDirectives.isSpecified("defer")
        GraphQLSpecifiedDirectives.SPECIFIED == io.micronaut.graphql.tools.internal.GraphQLSpecifiedDirectives.SPECIFIED
    }

    def "other names, a different case, an empty name and null are not specified"() {
        expect:
        !GraphQLSpecifiedDirectives.isSpecified(name)

        where:
        name << ["Include", "", "custom", "auth", null]
    }
}
