package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType(name = "Magazine")
final class MagazineGraph {

    int getIssue() {
        return 1
    }

}
