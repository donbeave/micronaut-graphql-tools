package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

@Singleton
@GraphQLTypeResolver(Parcel.class)
public class ParcelResolver {

    public String getAuthor(Parcel parcel) {
        return "Jane";
    }

    public boolean isActive(Parcel parcel) {
        return true;
    }

}
