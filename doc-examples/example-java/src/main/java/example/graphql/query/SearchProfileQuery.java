package example.graphql.query;

import example.ElapsedTime;
import example.data.User;
import example.data.UserRepository;
import example.graphql.type.Profile;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;
import java.util.stream.Collectors;

@GraphQLRootResolver
public class SearchProfileQuery {

    private final UserRepository userRepository;

    public SearchProfileQuery(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @ElapsedTime
    public List<Profile> searchProfile(String contains, @Nullable Integer limit) {
        List<User> results = userRepository.search(contains);
        if (limit != null) {
            results = results.subList(0, Integer.min(limit, results.size()));
        }
        return results.stream()
                .map(Profile::new)
                .collect(Collectors.toList());
    }

}
