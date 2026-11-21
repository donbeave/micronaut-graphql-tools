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
package io.micronaut.graphql.tools.benchmarks.fixture;

import java.util.Map;

/** The sources and the schema of the {@link ClassElementFixture}: a resolver class, an input class with properties and an input class with constructor arguments. */
final class BindingFixtureSources {

    static final String PACKAGE = "bindingfixture";

    /** The annotation that marks the classes whose elements the fixture hands out. */
    static final String MARKER = PACKAGE + ".Bound";

    static final String SCHEMA = """
            type Query {
              book(id: ID!): Book
              bookCount: Int!
              books(filter: BookFilter): [Book!]!
              search(criteria: SearchCriteria!): [Book!]!
              drifted: Int!
            }

            type Book {
              id: ID!
              title: String!
              pages: Int!
            }

            input BookFilter {
              title: String
              minPages: Int
              tags: [String!]
            }

            input SearchCriteria {
              term: String!
              limit: Int!
            }
            """;

    private static final String IMPORTS = """
            package bindingfixture;

            import io.micronaut.core.annotation.Introspected;
            import io.micronaut.graphql.tools.annotation.GraphQLInput;
            import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
            import io.micronaut.graphql.tools.annotation.GraphQLType;
            import jakarta.inject.Singleton;
            import java.util.List;

            """;

    /** The class name of each source, with its text. */
    static final Map<String, String> SOURCES = Map.of(
            "Bound", """
                    package bindingfixture;

                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;

                    @Retention(RetentionPolicy.SOURCE)
                    public @interface Bound {
                    }
                    """,
            "Book", IMPORTS + """
                    @GraphQLType
                    public class Book {
                        public String getId() { return "id"; }
                        public String getTitle() { return "title"; }
                        public int getPages() { return 1; }
                    }
                    """,
            "BookFilter", IMPORTS + """
                    @GraphQLInput
                    public class BookFilter {
                        private String title;
                        private Integer minPages;
                        private List<String> tags;

                        public String getTitle() { return title; }
                        public void setTitle(String title) { this.title = title; }
                        public Integer getMinPages() { return minPages; }
                        public void setMinPages(Integer minPages) { this.minPages = minPages; }
                        public List<String> getTags() { return tags; }
                        public void setTags(List<String> tags) { this.tags = tags; }
                    }
                    """,
            "SearchCriteria", IMPORTS + """
                    @GraphQLInput
                    public class SearchCriteria {
                        private final String term;
                        private final int limit;

                        public SearchCriteria(String term, int limit) {
                            this.term = term;
                            this.limit = limit;
                        }

                        public String getTerm() { return term; }
                        public int getLimit() { return limit; }
                    }
                    """,
            "BookQuery", IMPORTS + """
                    @Bound
                    @Singleton
                    @GraphQLRootResolver
                    public class BookQuery {
                        public Book book(String id) { return new Book(); }
                        public int bookCount() { return 1; }
                    }
                    """,
            "FilterQuery", IMPORTS + """
                    @Bound
                    @Singleton
                    @GraphQLRootResolver
                    public class FilterQuery {
                        public List<Book> books(BookFilter filter) { return List.of(); }
                    }
                    """,
            "DriftQuery", IMPORTS + """
                    @Bound
                    @Singleton
                    @GraphQLRootResolver
                    public class DriftQuery {
                        public int drifted(String undeclared) { return 1; }
                    }
                    """,
            "SearchQuery", IMPORTS + """
                    @Bound
                    @Singleton
                    @GraphQLRootResolver
                    public class SearchQuery {
                        public List<Book> search(SearchCriteria criteria) { return List.of(); }
                    }
                    """);

    private BindingFixtureSources() {
    }
}
