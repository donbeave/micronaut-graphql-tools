package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class Query implements Contract {

    @Override
    String size(Integer width, Integer length) {
        return 'long=' + length + ' short=' + width
    }

}
