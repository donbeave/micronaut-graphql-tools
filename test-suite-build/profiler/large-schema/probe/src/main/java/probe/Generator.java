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
package probe;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates the synthetic input of the measurement: a schema of 2,000 types and 250 operation documents against it. The output
 * is a pure function of the counts, so every run and every compiler sees the same text. Types refer to types of a lower index
 * (halves and thirds), so the reference graph is a tree of shallow depth: graphql-java builds a schema by recursion through the
 * references, and a chain of 1,349 types overflows the stack of a compiler thread.
 */
final class Generator {

    static final int OBJECTS = 1349;
    static final int INTERFACES = 100;
    static final int INPUTS = 300;
    static final int ENUMS = 150;
    static final int UNIONS = 100;
    static final int DOCUMENTS = 250;

    private Generator() {
    }

    /** @return the number of types of the schema, the root Query type included */
    static int typeCount() {
        return OBJECTS + 1 + INTERFACES + INPUTS + ENUMS + UNIONS;
    }

    static String schema() {
        StringBuilder sdl = new StringBuilder(2_000_000);
        sdl.append("schema { query: Query }\n");
        sdl.append("type Query {\n");
        for (int k = 0; k < DOCUMENTS; k++) {
            sdl.append("  t").append(k).append("(id: ID!): T").append(k * 5 % OBJECTS).append('\n');
            sdl.append("  u").append(k).append("(id: ID!): U").append(k % UNIONS).append('\n');
        }
        sdl.append("}\n");
        for (int i = 0; i < INTERFACES; i++) {
            sdl.append("interface I").append(i).append(" {\n  id: ID!\n  name: String\n}\n");
        }
        for (int i = 0; i < ENUMS; i++) {
            sdl.append("enum E").append(i).append(" { ALPHA BETA GAMMA }\n");
        }
        for (int i = 0; i < INPUTS; i++) {
            sdl.append("input In").append(i).append(" {\n  a: String\n  b: Int\n  e: E").append(i % ENUMS).append("\n}\n");
        }
        for (int i = 0; i < UNIONS; i++) {
            sdl.append("union U").append(i).append(" = T").append(i).append(" | T").append(i + 100).append(" | T").append(i + 200).append('\n');
        }
        for (int i = 0; i < OBJECTS; i++) {
            sdl.append("type T").append(i).append(" implements I").append(i % INTERFACES).append(" {\n")
                    .append("  id: ID!\n  name: String\n  score: Int\n")
                    .append("  kind: E").append(i % ENUMS).append('\n')
                    .append("  next: T").append(i / 2).append('\n')
                    .append("  peers(filter: In").append(i % INPUTS).append("): [T").append(i / 3).append("!]!\n")
                    .append("  related: U").append(i % UNIONS).append('\n')
                    .append("  tags(first: Int = 10): [String!]\n")
                    .append("}\n");
        }
        return sdl.toString();
    }

    static List<String> documents() {
        List<String> documents = new ArrayList<>(DOCUMENTS);
        for (int k = 0; k < DOCUMENTS; k++) {
            int t = k * 5 % OBJECTS;
            int peer = t / 3;
            documents.add("""
                    query Q%1$d($id: ID!, $filter: In%2$d, $first: Int) {
                      t%1$d(id: $id) {
                        ...Core%1$d
                        next {
                          id
                          name
                          kind
                          next { id score }
                        }
                        peers(filter: $filter) {
                          id
                          name
                          tags(first: $first)
                          ... on I%3$d { id name }
                          related {
                            __typename
                            ... on T%4$d { id score kind }
                          }
                        }
                      }
                      u%1$d(id: $id) {
                        __typename
                        ... on T%5$d { id name next { id } }
                      }
                    }
                    fragment Core%1$d on T%6$d {
                      id
                      name
                      score
                      kind
                      tags
                    }
                    """.formatted(k, t % INPUTS, peer % INTERFACES, peer % UNIONS, k % UNIONS, t));
        }
        return documents;
    }
}
