package example.data

import jakarta.inject.Singleton

@Singleton
class UserRepository {

    class UsernameAlreadyExistsException : RuntimeException()

    fun search(contains: String): List<User> = STATE.filter {
        it.username!!.contains(contains) || it.firstName!!.contains(contains) || it.lastName!!.contains(contains)
    }

    fun findByUsername(username: String): User? = STATE.firstOrNull { it.username == username }

    fun create(user: User) {
        if (findByUsername(user.username!!) != null) {
            throw UsernameAlreadyExistsException()
        }
        user.id = ++lastId
        STATE.add(user)
    }

    companion object {
        private var lastId = 0L
        private val STATE = mutableListOf(
            User(++lastId, "test", "Test", "Test", "test"),
            User(++lastId, "bob", "Bob", "Di", "bodi"),
            User(++lastId, "travis", "Travis", "Mot", "trav"),
        )
    }
}
