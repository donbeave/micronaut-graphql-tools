package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class Item {

    public String getName() {
        return "pen";
    }

    public String getLabel() {
        return "own";
    }

}
