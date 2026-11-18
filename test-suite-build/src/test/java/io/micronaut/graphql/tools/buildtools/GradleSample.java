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

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A copy of one of the sample projects, built by Gradle TestKit. The copy has its own project directory and its own build
 * cache, so a scenario edits and builds without touching the sample or another scenario, and the outcome of a task
 * (executed, up to date, from cache) is the build's own, never one that an earlier run left in a shared cache.
 */
final class GradleSample {

    private final Path dir;
    private final Path cache;
    private final List<String> arguments;

    private GradleSample(Path dir, Path cache, List<String> arguments) {
        this.dir = dir;
        this.cache = cache;
        this.arguments = arguments;
    }

    /**
     * @param name     the sample directory under {@code samples}
     * @param scenario a name that is unique to the scenario: the copy and the build cache are under it
     */
    static GradleSample copyOf(String name, String scenario) {
        Path scenarioDir = Environment.WORK.resolve("gradle").resolve(name).resolve(scenario);
        Files.deleteTree(scenarioDir);
        return at(name, scenarioDir.resolve("project"), scenarioDir.resolve("cache"));
    }

    /** The same sample, at another path, with the build cache of this one. */
    GradleSample relocated(String name, String to) {
        return at(name, dir.getParent().resolve(to), cache);
    }

    private static GradleSample at(String name, Path project, Path cache) {
        Files.deleteTree(project);
        Files.copyTree(Environment.SAMPLES.resolve(name), project);
        Files.write(project.resolve("gradle.properties"), String.join("\n",
                "graphqlToolsRepository=" + Environment.REPOSITORY.toUri(),
                "graphqlToolsVersion=" + Environment.GRAPHQL_TOOLS_VERSION,
                "micronautPlatformVersion=" + Environment.MICRONAUT_PLATFORM,
                "micronautGradlePluginVersion=" + Environment.MICRONAUT_GRADLE_PLUGIN,
                "kotlinVersion=" + Environment.KOTLIN,
                "kspVersion=" + Environment.KSP,
                "groovyVersion=" + Environment.GROOVY,
                "sampleBuildCache=" + cache.toAbsolutePath().toString().replace('\\', '/'),
                "org.gradle.caching=true",
                // a schema edit made right after a build can reach the daemon after the next build has read the old file
                "org.gradle.vfs.watch=false",
                "org.gradle.jvmargs=-Xmx1g -Dfile.encoding=UTF-8",
                "") + "\n");
        return new GradleSample(project, cache, List.of());
    }

    Path file(String relativePath) {
        return dir.resolve(relativePath);
    }

    Path dir() {
        return dir;
    }

    BuildResult build(String... tasks) {
        return runner(tasks).build();
    }

    BuildResult buildAndFail(String... tasks) {
        return runner(tasks).buildAndFail();
    }

    private GradleRunner runner(String... tasks) {
        List<String> all = new ArrayList<>(arguments);
        all.add("--console=plain");
        all.add("--stacktrace");
        all.addAll(List.of(tasks));
        return GradleRunner.create()
                .withProjectDir(dir.toFile())
                .withTestKitDir(Environment.GRADLE_USER_HOME.toFile())
                .withArguments(all);
    }

    static TaskOutcome outcome(BuildResult result, String task) {
        BuildTask buildTask = result.task(task);
        if (buildTask == null) {
            throw new AssertionError("The task " + task + " did not run. Tasks: " + result.getTasks()
                    + "\n" + result.getOutput());
        }
        return buildTask.getOutcome();
    }
}
