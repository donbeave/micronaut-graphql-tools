package example;

import io.micronaut.graphql.tools.annotation.GraphQLInput;

@GraphQLInput
public class Filter {

    private int size;

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

}
