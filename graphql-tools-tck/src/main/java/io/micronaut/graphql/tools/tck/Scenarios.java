package io.micronaut.graphql.tools.tck;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Reads the scenario files from the classpath. Nothing is scanned: {@code tck/scenarios.txt} lists the scenarios and
 * {@code tck/<id>/cases.txt} the cases of each, because a native image cannot list resources.
 */
public final class Scenarios {

    static final String ROOT = "tck/";

    private Scenarios() {
    }

    /**
     * @return the ids of all scenarios, in the order of {@code tck/scenarios.txt}
     */
    public static List<String> ids() {
        return lines(ROOT + "scenarios.txt");
    }

    /**
     * @param id the scenario id
     * @return the schema of the scenario ({@code tck/<id>/schema.graphqls}), for the fixtures of a suite to serve
     */
    public static String schema(String id) {
        return text(ROOT + id + "/schema.graphqls");
    }

    /**
     * @param id the scenario id
     * @return the scenario with all its files read
     */
    public static Scenario load(String id) {
        String dir = ROOT + id + "/";
        Map<String, String> config = new LinkedHashMap<>();
        if (exists(dir + "config.properties")) {
            Properties properties = new Properties();
            try (InputStream in = open(dir + "config.properties")) {
                properties.load(in);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            properties.stringPropertyNames().stream().sorted().forEach(key -> config.put(key, properties.getProperty(key)));
        }
        List<Scenario.Case> cases = new ArrayList<>();
        for (String name : lines(dir + "cases.txt")) {
            cases.add(new Scenario.Case(
                    name,
                    text(dir + name + ".graphql"),
                    exists(dir + name + ".variables.json") ? text(dir + name + ".variables.json") : null,
                    exists(dir + name + ".operation") ? text(dir + name + ".operation").strip() : null,
                    text(dir + name + ".expected.json")));
        }
        return new Scenario(id, config, cases);
    }

    static boolean exists(String resource) {
        return Scenarios.class.getClassLoader().getResource(resource) != null;
    }

    static String text(String resource) {
        try (InputStream in = open(resource)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @param resource the resource with one entry per line
     * @return the entries: blank lines and lines that start with {@code #} are dropped
     */
    static List<String> lines(String resource) {
        return text(resource).lines().map(String::strip).filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
    }

    private static InputStream open(String resource) {
        InputStream in = Scenarios.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("The TCK resource " + resource + " is missing");
        }
        return in;
    }

}
