package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class TextSection implements Section {

    @Override
    public String getTitle() {
        return "text";
    }

    public String getSummary() {
        return "own";
    }

}
