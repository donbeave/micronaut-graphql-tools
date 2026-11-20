package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class Query {

    List<String> getBooks(int limit) {
        return []
    }

    String getVersion() {
        return '1'
    }

}
