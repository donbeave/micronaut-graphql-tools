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

import java.util.List;
import java.util.Map;

/**
 * One scenario: the files under {@code tck/<id>/} on the classpath.
 *
 * @param id     the directory name, also the value of the {@code tck.scenario} property that activates the fixtures
 * @param config the {@code config.properties} of the scenario, applied to the application of the suite
 * @param cases  the operations to execute, in the order of {@code cases.txt}
 */
public record Scenario(String id, Map<String, String> config, List<Case> cases) {

    /**
     * One operation and the response it must get.
     *
     * @param name          the file name base of the case
     * @param query         the operation document
     * @param variablesJson the variables as a JSON object, {@code null} when the case has none
     * @param operationName the operation to run in a document of several, {@code null} when the case does not name one
     * @param expectedJson  the complete expected HTTP response body: data, errors and extensions
     */
    public record Case(String name, String query, String variablesJson, String operationName, String expectedJson) {
    }

}
