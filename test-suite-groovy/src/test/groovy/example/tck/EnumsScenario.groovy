package example.tck

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.Nullable
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

// The fixtures of the TCK scenario `enums`.

@Introspected
enum Level {
    LOW, MEDIUM, HIGH
}

@CompileStatic
@GraphQLInput
class Window {
    Level from
    Level to
    List<Level> only
}

@CompileStatic
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
