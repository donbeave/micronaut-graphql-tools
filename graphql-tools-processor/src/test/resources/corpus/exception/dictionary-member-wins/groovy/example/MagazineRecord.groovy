package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class MagazineRecord implements SearchResult {

    int getIssue() {
        return 1
    }

}
