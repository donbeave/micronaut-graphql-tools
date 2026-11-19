package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class Query {

    public String getVersion() {
        return "1";
    }

    public Parcel parcel() {
        return new Parcel();
    }

}
