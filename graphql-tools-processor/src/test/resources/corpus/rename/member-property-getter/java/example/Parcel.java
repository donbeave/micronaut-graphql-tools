package example;

import io.micronaut.graphql.tools.annotation.GraphQLField;
import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class Parcel {

    public String getId() {
        return "1";
    }

    @GraphQLField("headline")
    public String getTitle() {
        return "Fragile";
    }

}
