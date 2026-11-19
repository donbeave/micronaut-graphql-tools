package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class Magazine {

    public int getIssue() {
        return 1;
    }

}
