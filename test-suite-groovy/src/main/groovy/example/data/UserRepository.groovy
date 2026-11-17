package example.data

import groovy.transform.CompileStatic
import jakarta.inject.Singleton

@CompileStatic
@Singleton
class UserRepository {

    static class UsernameAlreadyExistsException extends RuntimeException {
    }

    private static long lastId = 0L
    private static final List<User> STATE = [
            new User(id: ++lastId, username: 'test', firstName: 'Test', lastName: 'Test', password: 'test'),
            new User(id: ++lastId, username: 'bob', firstName: 'Bob', lastName: 'Di', password: 'bodi'),
            new User(id: ++lastId, username: 'travis', firstName: 'Travis', lastName: 'Mot', password: 'trav'),
    ]

    List<User> search(String contains) {
        STATE.findAll {
            it.username.contains(contains) || it.firstName.contains(contains) || it.lastName.contains(contains)
        }
    }

    User findByUsername(String username) {
        STATE.find { it.username == username }
    }

    void create(User user) {
        if (findByUsername(user.username) != null) {
            throw new UsernameAlreadyExistsException()
        }
        user.id = ++lastId
        STATE.add(user)
    }
}
