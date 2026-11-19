package example;

import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

@GraphQLTypeResolver(Parcel.class)
public class ParcelResolver {

    public String author(Parcel parcel) {
        return "Jane";
    }

    public String getAuthor(Parcel parcel) {
        return "John";
    }

    public boolean isActive(Parcel parcel) {
        return true;
    }

}
