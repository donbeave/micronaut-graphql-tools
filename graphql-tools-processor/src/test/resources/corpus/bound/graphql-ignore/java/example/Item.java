package example;

import io.micronaut.graphql.tools.annotation.GraphQLIgnore;
import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class Item {

    public String getName() {
        return "pen";
    }

    @GraphQLIgnore
    public String getLabel() {
        return "label";
    }

}
