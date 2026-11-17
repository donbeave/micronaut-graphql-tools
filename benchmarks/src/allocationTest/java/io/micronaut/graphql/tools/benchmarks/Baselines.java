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
            writer.write("# Written by: ./gradlew :benchmarks:allocationTest -Pallocation.update\n");
            writer.write("# Review the diff: a higher number is an allocation the change added.\n");
            for (Map.Entry<String, String> entry : values.entrySet()) {
                writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
            }
        }
    }
}
