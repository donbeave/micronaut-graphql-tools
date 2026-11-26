package example;

import io.micronaut.graphql.tools.annotation.GraphQLField;
import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class Slip {

    @GraphQLField("title")
    public String first() {
        return "first";
    }

    @GraphQLField("title")
    public String second() {
        return "second";
    }

}
