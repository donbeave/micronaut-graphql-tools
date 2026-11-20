package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

@Singleton
@GraphQLTypeResolver(Parcel.class)
public class ParcelResolver {

    @GraphQLSchemaCoordinate("Parcel.class")
    public String kind(Parcel parcel) {
        return "parcel";
    }

    @GraphQLSchemaCoordinate("Parcel.class")
    public String type(Parcel parcel) {
        return "other";
    }

}
