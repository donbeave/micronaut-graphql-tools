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
package example.tck

import jakarta.inject.Singleton
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.Nullable
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

// The fixtures of the TCK scenario `enums`.

// tag::enums[]
@Introspected
enum Level {
    LOW, MEDIUM, HIGH
}
// end::enums[]

@CompileStatic
@GraphQLInput
class Window {
    Level from
    Level to
    List<Level> only
}

// tag::enums[]
@CompileStatic
@Singleton
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'enums')
class EnumsQueries {

    int rank(Level level) {
        return level.ordinal()
    }

    Level next(Level level) {
        return Level.values()[(level.ordinal() + 1) % Level.values().length]
    }

    List<Level> pick(List<Level> levels) {
        return levels.sort(false)
    }

    @Nullable
    Level maybe(@Nullable Level level) {
        return level
    }

    String window(Window window) {
        return "from=$window.from to=$window.to only=$window.only".toString()
    }
}
// end::enums[]
