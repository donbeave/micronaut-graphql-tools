package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@Singleton
@GraphQLTypeResolver(Slip)
class SlipResolver {

    String title(Slip slip) {
        return 'resolver'
    }

}
