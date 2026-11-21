package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class MagazineModel implements SearchResult {

    int issue = 1

}
