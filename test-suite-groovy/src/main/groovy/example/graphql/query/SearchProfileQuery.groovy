package example.graphql.query

import example.ElapsedTime
import example.data.User
import example.data.UserRepository
import example.graphql.type.Profile
import groovy.transform.CompileStatic
import io.micronaut.core.annotation.Nullable
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@CompileStatic
@GraphQLRootResolver
class SearchProfileQuery {

    private final UserRepository userRepository

    SearchProfileQuery(UserRepository userRepository) {
        this.userRepository = userRepository
    }

    @ElapsedTime
    List<Profile> searchProfile(String contains, @Nullable Integer limit) {
        List<User> results = userRepository.search(contains)
        if (limit != null) {
            results = results.subList(0, Integer.min(limit, results.size()))
        }
        return results.collect { new Profile(it) }
    }
}
