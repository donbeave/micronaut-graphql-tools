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

import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

// The fixtures of the TCK scenario `enums`.

@Introspected
enum class Level { LOW, MEDIUM, HIGH }

@GraphQLInput
class Window {
    var from: Level? = null
    var to: Level? = null
    var only: List<Level>? = null
}

@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "enums")
class EnumsQueries {

    fun rank(level: Level): Int = level.ordinal

    fun next(level: Level): Level = Level.entries[(level.ordinal + 1) % Level.entries.size]

    fun pick(levels: List<Level>): List<Level> = levels.sorted()

    fun maybe(level: Level?): Level? = level

    fun window(window: Window): String = "from=${window.from} to=${window.to} only=${window.only}"
}
