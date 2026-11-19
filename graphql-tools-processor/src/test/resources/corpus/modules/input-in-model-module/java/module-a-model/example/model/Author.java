package example.model;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class Author {

    private final String id;
    private final String name;

    public Author(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

}
