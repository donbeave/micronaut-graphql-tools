package io.micronaut.graphql.tools.tck;

import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scenario files themselves: every scenario is listed and complete, every case file is listed, every JSON file parses.
 * A suite runs the scenarios; this runs without any suite, so a broken file fails here first and fast.
 */
class ScenarioFilesTest {

    private static Path root() throws URISyntaxException {
        return Paths.get(ScenarioFilesTest.class.getClassLoader().getResource("tck/scenarios.txt").toURI()).getParent();
    }

    @Test
    void everyScenarioDirectoryIsListedAndEveryListedScenarioExists() throws Exception {
        Set<String> directories = new TreeSet<>();
        try (Stream<Path> children = Files.list(root())) {
            children.filter(Files::isDirectory).forEach(dir -> directories.add(dir.getFileName().toString()));
        }
        assertEquals(directories, new TreeSet<>(Scenarios.ids()),
                "tck/scenarios.txt and the scenario directories differ: list every directory, and no other name");
        assertEquals(Scenarios.ids().size(), new TreeSet<>(Scenarios.ids()).size(), "tck/scenarios.txt lists a scenario twice");
    }

    @Test
    void everyScenarioIsCompleteAndHasNoStrayFile() throws Exception {
        List<String> problems = new ArrayList<>();
        for (String id : Scenarios.ids()) {
            Path dir = root().resolve(id);
            Set<String> expectedFiles = new TreeSet<>(Set.of("schema.graphqls", "cases.txt"));
            if (Files.exists(dir.resolve("config.properties"))) {
                expectedFiles.add("config.properties");
            }
            List<String> cases = Scenarios.lines("tck/" + id + "/cases.txt");
            if (cases.isEmpty()) {
                problems.add(id + " has no case");
            }
            if (new TreeSet<>(cases).size() != cases.size()) {
                problems.add(id + "/cases.txt lists a case twice");
            }
            for (String name : cases) {
                expectedFiles.add(name + ".graphql");
                expectedFiles.add(name + ".expected.json");
                for (String optional : List.of(".variables.json", ".operation")) {
                    if (Files.exists(dir.resolve(name + optional))) {
                        expectedFiles.add(name + optional);
                    }
                }
            }
            Set<String> actualFiles = new TreeSet<>();
            try (Stream<Path> children = Files.list(dir)) {
                children.forEach(file -> actualFiles.add(file.getFileName().toString()));
            }
            Set<String> missing = new TreeSet<>(expectedFiles);
            missing.removeAll(actualFiles);
            Set<String> stray = new TreeSet<>(actualFiles);
            stray.removeAll(expectedFiles);
            if (!missing.isEmpty()) {
                problems.add(id + " misses " + missing);
            }
            if (!stray.isEmpty()) {
                problems.add(id + " has files that cases.txt does not list: " + stray);
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void everyJsonFileParsesAndTheLoadedScenariosAreWhole() throws Exception {
        JsonMapper json = JsonMapper.createDefault();
        for (String id : Scenarios.ids()) {
            Scenario scenario = Scenarios.load(id);
            assertFalse(Scenarios.schema(id).isBlank(), id + " has an empty schema");
            for (Scenario.Case testCase : scenario.cases()) {
                String where = id + "/" + testCase.name();
                assertFalse(testCase.query().isBlank(), where + ": the operation is empty");
                Object expected = json.readValue(testCase.expectedJson(), Argument.OBJECT_ARGUMENT);
                assertTrue(expected instanceof java.util.Map<?, ?> response
                        && (response.containsKey("data") || response.containsKey("errors")), where + ": expected.json is not a response");
                if (testCase.variablesJson() != null) {
                    assertTrue(json.readValue(testCase.variablesJson(), Argument.OBJECT_ARGUMENT) instanceof java.util.Map,
                            where + ": variables.json is not an object");
                }
            }
        }
    }

}
