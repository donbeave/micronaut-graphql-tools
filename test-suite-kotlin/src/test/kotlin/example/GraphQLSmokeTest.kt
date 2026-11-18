package example

import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.graphql.tools.BindingManifest
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

@MicronautTest
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class GraphQLSmokeTest(@Client("/") private val client: HttpClient, private val manifest: BindingManifest) {

    @Suppress("UNCHECKED_CAST")
    private fun execute(query: String, variables: Map<String, Any> = HashMap()): Map<String, Any?> {
        val response = client.toBlocking().retrieve(
            HttpRequest.POST("/graphql", mapOf("query" to query, "variables" to variables)),
            Argument.mapOf(Argument.STRING, Argument.OBJECT_ARGUMENT),
        )
        assertNull(response["errors"], "GraphQL errors: ${response["errors"]}")
        return response["data"] as Map<String, Any?>
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.obj(key: String) = this[key] as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.list(key: String) = this[key] as List<Map<String, Any?>>

    @Test
    @Order(1)
    fun addBookRequiresSignIn() {
        val payload = execute(
            "mutation(\$id: ID!) { addBook(id: \$id) { error { __typename ... on SecurityError { securityCode: code message } } } }",
            mapOf("id" to "ab"),
        ).obj("addBook")
        val error = payload.obj("error")
        assertEquals("SecurityError", error["__typename"])
        assertEquals("NOT_AUTHENTICATED", error["securityCode"])
    }

    @Test
    @Order(2)
    fun allBooksResolvesTypeResolversAndDataLoader() {
        val books = execute("{ allBooks { id title year label sellers { name price } } }").list("allBooks")
        assertEquals(5, books.size)
        val first = books.first()
        assertEquals("ab", first["id"])
        assertEquals("The Fractured Sage", first["title"])
        assertEquals(1982, first["year"])
        assertEquals("The Fractured Sage (1982)", first["label"])
        val sellers = first.list("sellers")
        assertEquals(listOf("Amaz", "Yah"), sellers.map { it["name"] })
        assertEquals(listOf(20.0, 21.0), sellers.map { (it["price"] as Number).toDouble() })
    }

    @Test
    @Order(3)
    fun introductionAdviceResolverBindsItsAbstractMethod() {
        assertEquals("introduced introduced", execute("{ introduced }")["introduced"])
    }

    @Test
    @Order(4)
    fun searchProfileWithLimit() {
        val profiles = execute(
            "query(\$contains: String!, \$limit: Int) { searchProfile(contains: \$contains, limit: \$limit) { username fullName } }",
            mapOf("contains" to "T", "limit" to 1),
        ).list("searchProfile")
        assertEquals(1, profiles.size)
        assertEquals("test", profiles.first()["username"])
        assertEquals("Test Test", profiles.first()["fullName"])
    }

    @Test
    @Order(5)
    fun signInFailures() {
        val query = "mutation(\$u: String!, \$p: String!) { signIn(username: \$u, password: \$p) { data { username } error { __typename ... on ValidationError { validationCode: code message } ... on SecurityError { securityCode: code message } } } }"
        val unknown = execute(query, mapOf("u" to "unknown-user", "p" to "test")).obj("signIn")
        assertNull(unknown["data"])
        assertEquals("NOT_FOUND", unknown.obj("error")["validationCode"])
        assertEquals("User unknown-user not found.", unknown.obj("error")["message"])

        val wrong = execute(query, mapOf("u" to "test", "p" to "xyz")).obj("signIn")
        assertNull(wrong["data"])
        assertEquals("SecurityError", wrong.obj("error")["__typename"])
        assertEquals("INCORRECT_CREDENTIALS", wrong.obj("error")["securityCode"])
    }

    @Test
    @Order(6)
    fun signUpThenDuplicate() {
        val query = "mutation(\$input: SignUpInput!) { signUp(input: \$input) { data { username firstName lastName books { id } } error { __typename ... on ValidationError { validationCode: code message } } } }"
        val input = mapOf("username" to "john", "password" to "test", "firstName" to "John", "lastName" to "Johnathan")
        val created = execute(query, mapOf("input" to input)).obj("signUp")
        assertNull(created["error"])
        assertEquals("john", created.obj("data")["username"])
        assertTrue(created.obj("data").list("books").isEmpty())

        val duplicate = execute(query, mapOf("input" to input)).obj("signUp")
        assertNull(duplicate["data"])
        assertEquals("DUPLICATE", duplicate.obj("error")["validationCode"])
        assertEquals("User with username john already exists.", duplicate.obj("error")["message"])
    }

    @Test
    @Order(7)
    fun signInThenAddBook() {
        val signIn = execute("mutation { signIn(username: \"bob\", password: \"bodi\") { data { username } } }").obj("signIn")
        assertEquals("bob", signIn.obj("data")["username"])

        assertNull(execute("mutation { addBook(id: \"ax\") { error { __typename } } }").obj("addBook")["error"])

        val current = execute("{ currentUser { username books { id label } } }").obj("currentUser")
        assertEquals("bob", current["username"])
        assertEquals(listOf("ax"), current.list("books").map { it["id"] })
        assertNotNull(current.list("books").first()["label"])
    }

    @Test
    @Order(8)
    fun ignoredAndEventListenerMethodsPassStrictModeAndTheManifestListsTheIgnoredOne() {
        assertTrue(manifest.diagnostics.none { it.contains("onStartup") || it.contains("helper") }, manifest.diagnostics.toString())
        assertEquals(
            listOf("~ignored example.graphql.query.AllBooksQuery#helper(java.lang.String)"),
            manifest.lines.filter { it.startsWith("~ignored ") },
        )
    }
}
