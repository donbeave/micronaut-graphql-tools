package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;

@Singleton
@GraphQLRootResolver
public class Query {

    @GraphQLSchemaCoordinate("Query.default")
    public String defaultValue() {
        return "plain";
    }

    @GraphQLSchemaCoordinate("Query.parcel")
    public Parcel fetchParcel() {
        return new Parcel();
    }

}
