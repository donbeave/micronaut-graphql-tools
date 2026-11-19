package example

import io.micronaut.graphql.tools.annotation.GraphQLField
import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class Parcel {

    String id = '1'

    @GraphQLField("headline")
    String title = 'Fragile'

}
