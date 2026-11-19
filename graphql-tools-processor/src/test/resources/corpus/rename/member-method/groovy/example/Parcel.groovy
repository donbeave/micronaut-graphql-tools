package example

import io.micronaut.graphql.tools.annotation.GraphQLField
import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class Parcel {

    String id = '1'

    @GraphQLField("class")
    String kind() {
        return 'parcel'
    }

    @GraphQLField("hashCode")
    int digest() {
        return 42
    }

}
