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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** A copy of one of the Maven sample projects, built with the {@code mvn} on the path. */
final class MavenSample {

    /** What a Maven run ended with. */
    record Result(int exitCode, String output) {
    }

    /** The waits before each retry of a run that failed on a download: 5 s, then 15 s, then 45 s. */
    private static final long[] RETRY_WAITS_SECONDS = {5, 15, 45};

    /** The download and resolution failures that a rerun can fix: Maven Central's rate limit, a dropped or timed out connection. */
    private static final Pattern TRANSIENT_FAILURE = Pattern.compile(
            "Could not transfer artifact|Non-resolvable parent POM|Could not resolve dependencies|Connection reset|Read timed out"
                    + "|Received status code (?:429|5\\d\\d)");

    /** The failures that a rerun does not fix: a compilation error, a test failure, or a processor diagnostic such as {@code [argument-mismatch] Query.books:}. */
    private static final Pattern REAL_FAILURE = Pattern.compile(
            "COMPILATION ERROR|Compilation failure|There (?:are|were) test failures|<<< (?:FAILURE|ERROR)!|Failures: [1-9]|Errors: [1-9]"
                    + "|\\[[a-z]+(?:-[a-z]+)+] \\S+:");

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

    /**
     * Whether a run is retried: it failed, its output names a download or resolution failure, and it failed for no other reason. A
     * test, a compilation error or a processor diagnostic is never retried, so the assertions on it see the first output.
     */
    static boolean isTransientFailure(int exitCode, String output) {
        return exitCode != 0 && TRANSIENT_FAILURE.matcher(output).find() && !REAL_FAILURE.matcher(output).find();
    }

    /** Runs {@code mvn -B -ntp -U <arguments>} in the project and waits for it; a transient failure is run again after a wait. */
    Result run(String... arguments) {
        Result result = start(arguments);
        for (int retry = 0; retry < RETRY_WAITS_SECONDS.length && isTransientFailure(result.exitCode(), result.output()); retry++) {
            long wait = RETRY_WAITS_SECONDS[retry];
            System.err.println("mvn retry " + (retry + 1) + " of " + RETRY_WAITS_SECONDS.length + " in " + dir + " after " + wait
                    + " s: a dependency download failed");
            pause(wait);
            result = start(arguments);
        }
        return result;
    }

    private static void pause(long seconds) {
        try {
            Thread.sleep(Duration.ofSeconds(seconds));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private Result start(String... arguments) {
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
