package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@Singleton
@GraphQLTypeResolver(Slip::class)
class SlipResolver {

    fun title(slip: Slip): String = "resolver"

}
