/*
 * Copyright 2021-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package example.relay

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import jakarta.inject.Singleton
import java.util.Base64

// tag::resolver[]
@Requires(env = [RELAY])
@Singleton
@GraphQLRootResolver
open class ArticlesQuery {

    private val articles = (1..5).map { Article(it.toString(), "Article $it") }

    open fun articles(first: Int, after: String?): ArticleConnection {
        require(first >= 0) { "first must not be negative" }
        val start = if (after == null) 0 else offset(after) + 1
        val end = start + minOf(first, maxOf(0, articles.size - start))
        val edges = (start until end).map { ArticleEdge(cursor(it), articles[it]) }
        val pageInfo = PageInfo(hasNextPage = end < articles.size, hasPreviousPage = start > 0, startCursor = edges.firstOrNull()?.cursor, endCursor = edges.lastOrNull()?.cursor)
        return ArticleConnection(edges, pageInfo, articles.size)
    }

    private fun cursor(offset: Int): String = Base64.getEncoder().encodeToString("offset:$offset".toByteArray())

    private fun offset(cursor: String): Int = String(Base64.getDecoder().decode(cursor)).removePrefix("offset:").toInt()
}
// end::resolver[]
