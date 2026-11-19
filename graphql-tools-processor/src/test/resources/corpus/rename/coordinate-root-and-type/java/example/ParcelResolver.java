package example;

import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

@GraphQLTypeResolver(Parcel.class)
public class ParcelResolver {

    @GraphQLSchemaCoordinate("Parcel.class")
    public String kind(Parcel parcel) {
        return "parcel";
    }

}
