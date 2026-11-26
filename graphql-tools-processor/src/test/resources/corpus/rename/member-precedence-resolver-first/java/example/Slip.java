package example;

import io.micronaut.graphql.tools.annotation.GraphQLField;
import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class Slip {

    @GraphQLField("title")
    public String heading() {
        return "member";
    }

}
