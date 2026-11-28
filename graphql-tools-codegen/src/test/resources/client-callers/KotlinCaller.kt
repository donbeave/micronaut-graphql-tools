package com.example.caller

import com.example.model.BookBadge
import com.example.model.BookCard
import com.example.model.FindNodeNode
import com.example.model.FindNodeNodeFolder
import com.example.model.FindNodeNodeOther
import com.example.model.GetBookBook
import com.example.model.GetUser
import com.example.model.GetUserVariables
import com.example.model.PostAdded
import com.example.model.PostAddedPostAdded
import com.example.model.Role
import com.example.model.SearchSearch
import com.example.model.SearchSearchFile
import com.example.model.SearchSearchFolder
import com.example.model.SearchSearchOther
import com.example.model.SearchSearchUser
import com.example.model.Status
import io.micronaut.graphql.tools.client.GraphQLRequest

object KotlinCaller {

    fun request(id: String): GetUser = GetUser(GetUserVariables(id, true, null))

    fun lowLevel(request: GetUser): GraphQLRequest<Map<String, com.example.model.GetUserUser>> =
        GraphQLRequest.builder(GetUser.DATA)
            .document(request.document())
            .operationName(request.name())
            .operationType(request.kind())
            .variable("id", request.variables().id)
            .build()

    // exhaustive without an else branch: the interface is sealed
    fun describe(result: SearchSearch): String = when (result) {
        is SearchSearchFolder -> "folder ${result.name} ${result.createdAt}"
        is SearchSearchFile -> "file ${result.bytes}"
        is SearchSearchUser -> "user ${result.id}"
        is SearchSearchOther -> "other ${result.__typename}"
    }

    fun node(node: FindNodeNode): String = when (node) {
        is FindNodeNodeFolder -> "${node.name} ${node.size} ${node.children.size}"
        is FindNodeNodeOther -> node.__typename
    }

    // a nullable component is a nullable type, a non-null one is not
    fun subtitle(book: GetBookBook): String = book.subtitle ?: "none"

    fun card(book: GetBookBook): String {
        val card: BookCard = book
        val badge: BookBadge = book
        // the accessors of an interface are methods in Kotlin; the components of a record are properties
        val tags: List<String?> = card.tags()
        return "${card.title()} $tags ${badge.inStock()} ${badge.rating()} ${book.title}"
    }

    fun status(status: Status): Boolean = status == Status.UNKNOWN__

    fun role(role: Role): String = when (role) {
        Role.ADMIN -> "admin"
        Role.MEMBER -> "member"
        Role.UNKNOWN__ -> "unknown"
    }

    fun likes(post: PostAddedPostAdded): Int? = post.likes

    fun kind() = PostAdded().kind()
}
