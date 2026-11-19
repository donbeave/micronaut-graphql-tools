package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class Query {

    List<String> getBooks(int limit) {
        return []
    }

    String getVersion() {
        return '1'
    }

}
