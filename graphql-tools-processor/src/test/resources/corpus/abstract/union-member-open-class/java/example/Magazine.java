package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType(name = "Magazine")
public class Magazine {

    public int getIssue() {
        return 1;
    }

}
