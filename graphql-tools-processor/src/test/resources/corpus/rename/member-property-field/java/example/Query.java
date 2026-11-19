package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class Query {

    public Parcel parcel() {
        return new Parcel();
    }

}
