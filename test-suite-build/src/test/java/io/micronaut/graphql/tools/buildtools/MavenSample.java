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
package io.micronaut.graphql.tools.buildtools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** A copy of one of the Maven sample projects, built with the {@code mvn} on the path. */
final class MavenSample {

    /** What a Maven run ended with. */
    record Result(int exitCode, String output) {
    }

    private final Path dir;

    private MavenSample(Path dir) {
        this.dir = dir;
    }

    /**
     * @param name     the sample directory under {@code samples}
     * @param scenario a name that is unique to the scenario: the copy is under it
     */
    static MavenSample copyOf(String name, String scenario) {
        Path project = Environment.WORK.resolve("maven").resolve(name).resolve(scenario).resolve("project");
        Files.deleteTree(project.getParent());
        Files.copyTree(Environment.SAMPLES.resolve(name), project);
        Path pom = project.resolve("pom.xml");
        Files.write(pom, Files.read(pom)
                .replace("@micronaut.platform@", Environment.MICRONAUT_PLATFORM)
                .replace("@kotlin@", Environment.KOTLIN)
                .replace("@groovy@", Environment.GROOVY));
        return new MavenSample(project);
    }

    Path file(String relativePath) {
        return dir.resolve(relativePath);
    }

    /** Runs {@code mvn -B -ntp -U <arguments>} in the project and waits for it. */
    Result run(String... arguments) {
        List<String> command = new ArrayList<>();
        command.add(System.getProperty("sample.maven", "mvn"));
        command.addAll(List.of("-B", "-ntp",
                // the library under test is a snapshot that every run publishes again
                "-U",
                // Maven Central answers HTTP 429 under load: retry it more often and further apart than the default of 3 at 5 s
                "-Daether.connector.http.retryHandler.count=5",
                "-Daether.connector.http.retryHandler.interval=10000",
                "-Dmaven.repo.local=" + Environment.WORK.resolve("maven-repository"),
                "-Dgraphql.tools.repository=" + Environment.REPOSITORY.toUri(),
                "-Dmicronaut.graphql.tools.version=" + Environment.GRAPHQL_TOOLS_VERSION));
        command.addAll(List.of(arguments));
        try {
            // the output goes to a file, so the timed wait is not blocked behind a read of a stalled stream
            Path log = dir.resolveSibling("mvn-" + System.nanoTime() + ".log");
            Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true)
                    .redirectOutput(log.toFile()).start();
            if (!process.waitFor(10, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IllegalStateException("mvn did not finish in 10 minutes in " + dir);
            }
            return new Result(process.exitValue(), java.nio.file.Files.readString(log, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("mvn cannot be started (it must be on the PATH: mise install maven): " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
