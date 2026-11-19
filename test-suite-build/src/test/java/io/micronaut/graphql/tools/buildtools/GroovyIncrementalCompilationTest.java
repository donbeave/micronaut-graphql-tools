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

class GroovyIncrementalCompilationTest extends IncrementalCompilationContract {

    @Override
    String sample() {
        return "groovy-gradle";
    }

    @Override
    String language() {
        return "groovy";
    }

    @Override
    String extension() {
        return "groovy";
    }

    @Override
    String processorDependency() {
        return "compileOnly(\"io.micronaut.graphql.tools:micronaut-graphql-tools-processor\")";
    }

    @Override
    String bean(String annotation, int number, int value) {
        String type = "Singleton".equals(annotation) ? "jakarta.inject.Singleton" : "io.micronaut.context.annotation." + annotation;
        return "package example\n\nimport " + type + "\n\n@" + annotation + "\nclass Filler" + number
                + " {\n\n    int value() {\n        " + value + "\n    }\n}\n";
    }

    @Override
    void changeBookInterface(Path book) {
        Files.replace(book, "    Book(String id, String title) {", "    int extra() {\n        1\n    }\n\n    Book(String id, String title) {");
    }
}
