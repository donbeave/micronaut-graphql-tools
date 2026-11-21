package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType(name = "TextSection")
public class TextSection implements Section {

    public String getTitle() {
        return "title";
    }

}
