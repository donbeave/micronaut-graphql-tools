package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class Query implements Contract {

    @Override
    String size(Integer width, Integer length) {
        return 'long=' + length + ' short=' + width
    }

}
