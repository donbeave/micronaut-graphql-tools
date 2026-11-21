package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class MagazineGraph implements SearchResult {

    int issue = 1

}
