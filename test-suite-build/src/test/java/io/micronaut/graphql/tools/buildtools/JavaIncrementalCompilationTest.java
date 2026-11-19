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

class JavaIncrementalCompilationTest extends IncrementalCompilationContract {

    @Override
    String sample() {
        return "java-gradle";
    }

    @Override
    String language() {
        return "java";
    }

    @Override
    String extension() {
        return "java";
    }

    @Override
    String processorDependency() {
        return "annotationProcessor(\"io.micronaut.graphql.tools:micronaut-graphql-tools-processor\")";
    }

    @Override
    String bean(String annotation, int number, int value) {
        String type = "Singleton".equals(annotation) ? "jakarta.inject.Singleton" : "io.micronaut.context.annotation." + annotation;
        return "package example;\n\nimport " + type + ";\n\n@" + annotation + "\npublic class Filler" + number
                + " {\n\n    public int value() {\n        return " + value + ";\n    }\n}\n";
    }

    @Override
    void changeBookInterface(Path book) {
        Files.replace(book, "public String getTitle() {", "public int extra() {\n        return 1;\n    }\n\n    public String getTitle() {");
    }

    @Override
    boolean zeroVisitsOnBodyEdit() {
        return true;
    }
}
