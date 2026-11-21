package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType(name = "Magazine")
class Magazine {

    int getIssue() {
        return 1
    }

}
