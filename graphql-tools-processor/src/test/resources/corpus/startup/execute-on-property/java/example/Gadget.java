package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;
import io.micronaut.scheduling.annotation.ExecuteOn;

@GraphQLType
public class Gadget {

    @ExecuteOn("io")
    public String getSize() {
        return "small";
    }

}
