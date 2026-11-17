package example.graphql.query

import example.ElapsedTime
import example.data.UserRepository
import example.graphql.type.Profile
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
open class SearchProfileQuery(private val userRepository: UserRepository) {

    @ElapsedTime
    open fun searchProfile(contains: String, limit: Int?): List<Profile> {
        var results = userRepository.search(contains)
        if (limit != null) {
            results = results.subList(0, minOf(limit, results.size))
        }
        return results.map { Profile(it) }
    }
}
