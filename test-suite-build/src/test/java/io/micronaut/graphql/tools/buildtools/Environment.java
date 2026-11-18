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

import java.nio.file.Path;

/** The locations and versions that the build of this module passes to the specs as system properties. */
final class Environment {

    static final Path SAMPLES = path("sample.dir");
    static final Path REPOSITORY = path("sample.repository");
    static final Path WORK = path("sample.work");
    static final Path REPORT = path("sample.report");
    static final Path GRADLE_USER_HOME = path("sample.gradle-user-home");
    static final String GRAPHQL_TOOLS_VERSION = property("sample.graphql-tools.version");
    static final String MICRONAUT_PLATFORM = property("sample.micronaut.platform");
    static final String MICRONAUT_GRADLE_PLUGIN = property("sample.micronaut-gradle-plugin");
    static final String KOTLIN = property("sample.kotlin");
    static final String KSP = property("sample.ksp");
    static final String GROOVY = property("sample.groovy");

    private Environment() {
    }

    private static Path path(String name) {
        return Path.of(property(name));
    }

    private static String property(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("The system property " + name + " is not set: run the specs with "
                    + "./gradlew :test-suite-build:buildToolsTest, which sets it");
        }
        return value;
    }
}
