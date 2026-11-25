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
package example.relay;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.IntStream;

// tag::resolver[]
@Requires(env = RelayFixtures.RELAY)
@Singleton
@GraphQLRootResolver
public class ArticlesQuery {

    private static final List<Article> ARTICLES = IntStream.rangeClosed(1, 5)
            .mapToObj(i -> new Article(String.valueOf(i), "Article " + i)).toList();

    public ArticleConnection articles(int first, @Nullable String after) {
        if (first < 0) {
            throw new IllegalArgumentException("first must not be negative");
        }
        int start = after == null ? 0 : offset(after) + 1;
        int end = start + Math.min(first, Math.max(0, ARTICLES.size() - start));
        List<ArticleEdge> edges = IntStream.range(start, end)
                .mapToObj(i -> new ArticleEdge(cursor(i), ARTICLES.get(i))).toList();
        PageInfo pageInfo = new PageInfo(end < ARTICLES.size(), start > 0,
                edges.isEmpty() ? null : edges.get(0).cursor(),
                edges.isEmpty() ? null : edges.get(edges.size() - 1).cursor());
        return new ArticleConnection(edges, pageInfo, ARTICLES.size());
    }

    private static String cursor(int offset) {
        return Base64.getEncoder().encodeToString(("offset:" + offset).getBytes(StandardCharsets.UTF_8));
    }

    private static int offset(String cursor) {
        return Integer.parseInt(new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8).substring("offset:".length()));
    }

}
// end::resolver[]
