package example;

import io.micronaut.graphql.tools.annotation.GraphQLField;
import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class Parcel {

    public String getId() {
        return "1";
    }

    @GraphQLField("class")
    public String kind() {
        return "parcel";
    }

    @GraphQLField("hashCode")
    public int digest() {
        return 42;
    }

}
