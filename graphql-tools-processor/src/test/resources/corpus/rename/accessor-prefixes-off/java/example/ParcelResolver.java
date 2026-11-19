package example;

import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

@GraphQLTypeResolver(Parcel.class)
public class ParcelResolver {

    public String getAuthor(Parcel parcel) {
        return "Jane";
    }

    public boolean isActive(Parcel parcel) {
        return true;
    }

}
