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
package io.micronaut.graphql.tools.benchmarks;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

final class Baselines {

    private final Path file;
    private final Map<String, String> values = new TreeMap<>();

    Baselines(Path file) throws IOException {
        this.file = file;
        if (Files.exists(file)) {
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(file)) {
                properties.load(reader);
            }
            properties.forEach((key, value) -> values.put((String) key, (String) value));
        }
    }

    static String key(String caseName, String leg, String jdk) {
        return caseName + "@" + leg + "@" + jdk;
    }

    Double get(String key) {
        String value = values.get(key);
        return value == null ? null : Double.valueOf(value);
    }

    boolean hasJdk(String jdk) {
        return values.keySet().stream().anyMatch(key -> key.endsWith("@" + jdk));
    }

    void put(String key, double bytesPerOperation) {
        values.put(key, String.format(java.util.Locale.ROOT, "%.2f", bytesPerOperation));
    }

    void write() throws IOException {
        try (Writer writer = Files.newBufferedWriter(file)) {
            writer.write("# Allocation baselines in bytes per operation, key: case@graphql-java leg@JDK feature version.\n");
            writer.write("# Written by: ./gradlew :test-suite-benchmarks:allocationTest -Pallocation.update\n");
            writer.write("# Review the diff: a higher number is an allocation the change added.\n");
            for (Map.Entry<String, String> entry : values.entrySet()) {
                writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
            }
        }
    }
}
