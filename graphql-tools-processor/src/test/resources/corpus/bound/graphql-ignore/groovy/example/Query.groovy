package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLIgnore
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
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
