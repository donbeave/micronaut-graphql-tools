package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class TextSection implements Section {

    @Override
    public String getId() {
        return "1";
    }

    public String getText() {
        return "text";
    }

}
