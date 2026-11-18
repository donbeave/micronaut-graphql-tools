package example.tck

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

// The fixtures of the TCK scenario `enums`.

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
