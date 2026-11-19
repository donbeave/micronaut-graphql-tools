package example;

import io.micronaut.graphql.tools.annotation.GraphQLField;
import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class Parcel {

    public String getId() {
        return "1";
    }

    public String getTitle() {
        return "Fragile";
    }

    @GraphQLField("title")
    public String heading() {
        return "Handle with care";
    }

}
