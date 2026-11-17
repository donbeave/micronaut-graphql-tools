package example

import io.micronaut.graphql.tools.annotation.GraphQLIgnore
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class Query {

    Item item() {
        return new Item()
    }

    @GraphQLIgnore
    String helper(String text) {
        return text
    }

}
