package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType(name = "Magazine")
final class Magazine implements SearchResult {

    int getIssue() {
        return 1
    }

}
