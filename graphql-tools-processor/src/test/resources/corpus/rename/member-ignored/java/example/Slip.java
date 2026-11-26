package example;

import io.micronaut.graphql.tools.annotation.GraphQLField;
import io.micronaut.graphql.tools.annotation.GraphQLIgnore;
import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class Slip {

    @GraphQLIgnore
    @GraphQLField("title")
    public String heading() {
        return "heading";
    }

}
