package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Singleton
@GraphQLRootResolver
public class Query {

    public String getVersion() {
        return "1";
    }

    public Parcel parcel() {
        return new Parcel();
    }

}
