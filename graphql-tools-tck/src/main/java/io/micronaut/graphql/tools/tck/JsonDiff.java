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
package io.micronaut.graphql.tools.tck;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Compares two parsed JSON documents structurally: objects by key (order does not matter), arrays by position, numbers by
 * value (so {@code 1} equals {@code 1.0}, whatever number type the JSON stack parsed). The expected string
 * {@value #ANY_STRING} matches any string: for the message of an error whose wording is graphql-java's and changes between
 * its versions, while the path, locations and extensions around it are still compared.
 */
final class JsonDiff {

    static final String ANY_STRING = "@any";

    private JsonDiff() {
    }

    /**
     * @param expected the parsed expected document
     * @param actual   the parsed actual document
     * @return one line per difference, with the JSON path; empty when the documents are equal
     */
    static List<String> diff(Object expected, Object actual) {
        List<String> differences = new ArrayList<>();
        compare("$", expected, actual, differences);
        return differences;
    }

    private static void compare(String path, Object expected, Object actual, List<String> out) {
        if (expected instanceof Map<?, ?> expectedMap) {
            if (!(actual instanceof Map<?, ?> actualMap)) {
                out.add(path + ": expected an object, found " + describe(actual));
                return;
            }
            for (Object key : new TreeSet<>(expectedMap.keySet().stream().map(String::valueOf).toList())) {
                if (!actualMap.containsKey(key)) {
                    out.add(path + "." + key + ": missing, expected " + describe(expectedMap.get(key)));
                } else {
                    compare(path + "." + key, expectedMap.get(key), actualMap.get(key), out);
                }
            }
            for (Object key : new TreeSet<>(actualMap.keySet().stream().map(String::valueOf).toList())) {
                if (!expectedMap.containsKey(key)) {
                    out.add(path + "." + key + ": unexpected, found " + describe(actualMap.get(key)));
                }
            }
        } else if (expected instanceof List<?> expectedList) {
            if (!(actual instanceof List<?> actualList)) {
                out.add(path + ": expected an array, found " + describe(actual));
                return;
            }
            if (expectedList.size() != actualList.size()) {
                out.add(path + ": expected " + expectedList.size() + " elements, found " + actualList.size());
                return;
            }
            for (int i = 0; i < expectedList.size(); i++) {
                compare(path + "[" + i + "]", expectedList.get(i), actualList.get(i), out);
            }
        } else if (expected instanceof Number expectedNumber) {
            if (!(actual instanceof Number actualNumber)
                    || new BigDecimal(expectedNumber.toString()).compareTo(new BigDecimal(actualNumber.toString())) != 0) {
                out.add(path + ": expected " + describe(expected) + ", found " + describe(actual));
            }
        } else if (ANY_STRING.equals(expected)) {
            if (!(actual instanceof String)) {
                out.add(path + ": expected a string, found " + describe(actual));
            }
        } else if (expected == null ? actual != null : !expected.equals(actual)) {
            out.add(path + ": expected " + describe(expected) + ", found " + describe(actual));
        }
    }

    private static String describe(Object value) {
        if (value instanceof CharSequence) {
            return "\"" + value + "\"";
        }
        return String.valueOf(value);
    }

}
