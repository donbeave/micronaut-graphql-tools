package example

import io.micronaut.graphql.tools.annotation.GraphQLField
import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class Parcel {

    private String title = 'Fragile'

    String id = '1'

    @GraphQLField("headline")
    String getTitle() {
        return title
    }

}
