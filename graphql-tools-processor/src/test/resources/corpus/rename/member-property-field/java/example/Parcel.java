package example;

import io.micronaut.graphql.tools.annotation.GraphQLField;
import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class Parcel {

    @GraphQLField("headline")
    private String title = "Fragile";

    public String getId() {
        return "1";
    }

    public String getTitle() {
        return title;
    }

}
