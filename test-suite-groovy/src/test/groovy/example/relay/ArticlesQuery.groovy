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

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import jakarta.inject.Singleton

// tag::resolver[]
@CompileStatic
@Requires(env = RelayFixtures.RELAY)
@Singleton
@GraphQLRootResolver
class ArticlesQuery {

    private static final List<Article> ARTICLES = (1..5).collect { int i ->
        new Article(id: i.toString(), title: "Article ${i}".toString())
    }

    ArticleConnection articles(int first, @Nullable String after) {
        if (first < 0) {
            throw new IllegalArgumentException('first must not be negative')
        }
        int start = after == null ? 0 : offset(after) + 1
        int end = start + Math.min(first, Math.max(0, ARTICLES.size() - start))
        List<ArticleEdge> edges = (start..<end).collect { int i ->
            new ArticleEdge(cursor: cursor(i), node: ARTICLES[i])
        }
        PageInfo pageInfo = new PageInfo(hasNextPage: end < ARTICLES.size(), hasPreviousPage: start > 0,
                startCursor: edges.empty ? null : edges.first().cursor,
                endCursor: edges.empty ? null : edges.last().cursor)
        return new ArticleConnection(edges: edges, pageInfo: pageInfo, totalCount: ARTICLES.size())
    }

    private static String cursor(int offset) {
        return Base64.encoder.encodeToString("offset:${offset}".toString().bytes)
    }

    private static int offset(String cursor) {
        int offset = new String(Base64.decoder.decode(cursor)).substring('offset:'.length()) as int
        if (offset < 0 || offset >= ARTICLES.size()) {
            throw new IllegalArgumentException('invalid cursor')
        }
        return offset
    }

}
// end::resolver[]
