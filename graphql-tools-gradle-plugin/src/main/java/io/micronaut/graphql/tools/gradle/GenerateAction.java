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
package io.micronaut.graphql.tools.gradle;

import io.micronaut.graphql.tools.codegen.Diagnostic;
import io.micronaut.graphql.tools.codegen.GeneratedFile;
import io.micronaut.graphql.tools.codegen.GenerationResult;
import io.micronaut.graphql.tools.codegen.GraphQLCodeGenerator;
import io.micronaut.graphql.tools.codegen.Options;
import io.micronaut.graphql.tools.codegen.OptionsResult;
import org.gradle.api.GradleException;
import org.gradle.workers.WorkAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The work that {@link GenerateGraphQLTask} submits. It runs in a class loader that holds the generator, reads the options from
 * their string form, runs the generator and writes each generated file whose content differs from what the file holds.
 * An error diagnostic fails the build with every message.
 */
public abstract class GenerateAction implements WorkAction<GenerateParameters> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateAction.class);

    @Override
    public void execute() {
        GenerateParameters parameters = getParameters();
        OptionsResult parsed = Options.fromMap(parameters.getOptions().get());
        report(parsed.diagnostics());
        fail(parsed.diagnostics());
        GenerationResult result = GraphQLCodeGenerator.builder()
                .withSchemaFiles(parameters.getSchemaFiles().getFiles().stream().map(file -> file.toPath()).toList())
                .withOutputDirectory(parameters.getOutputDirectory().get().getAsFile().toPath())
                .withOptions(parsed.options())
                .build()
                .generate();
        report(result.diagnostics());
        fail(result.diagnostics());
        Path root = parameters.getOutputDirectory().get().getAsFile().toPath().toAbsolutePath().normalize();
        for (GeneratedFile file : result.files()) {
            write(root, file);
        }
    }

    private static void report(List<Diagnostic> diagnostics) {
        for (Diagnostic diagnostic : diagnostics) {
            String text = "[" + diagnostic.code() + "] " + diagnostic.message();
            if (diagnostic.isError()) {
                LOGGER.error(text);
            } else {
                LOGGER.warn(text);
            }
        }
    }

    private static void fail(List<Diagnostic> diagnostics) {
        List<Diagnostic> errors = diagnostics.stream().filter(Diagnostic::isError).toList();
        if (!errors.isEmpty()) {
            throw new GradleException("The GraphQL code generator reported " + errors.size() + " error(s):\n"
                    + errors.stream().map(d -> "  [" + d.code() + "] " + d.message()).collect(Collectors.joining("\n")));
        }
    }

    private static void write(Path root, GeneratedFile file) {
        Path target = root.resolve(file.path()).normalize();
        if (!target.startsWith(root)) {
            throw new GradleException("The generator produced a file outside the output directory: " + file.path());
        }
        try {
            Files.createDirectories(target.getParent());
            byte[] content = file.content().getBytes(StandardCharsets.UTF_8);
            if (!Files.exists(target) || !java.util.Arrays.equals(Files.readAllBytes(target), content)) {
                Files.write(target, content);
            }
        } catch (IOException e) {
            throw new GradleException("Cannot write " + target, e);
        }
    }
}
