package example

import io.micronaut.graphql.tools.annotation.GraphQLField
import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class Parcel {

    String id = '1'

    String title = 'Fragile'

    @GraphQLField("title")
    String heading() {
        return 'Handle with care'
    }

}
