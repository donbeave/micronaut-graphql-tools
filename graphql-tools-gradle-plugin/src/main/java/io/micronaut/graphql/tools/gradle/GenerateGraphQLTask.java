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

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.TreeSet;

/**
 * Generates sources from the schema files of one {@link CodegenSpec}. The task is cacheable: its inputs are the schema directories, the
 * options in their string form and the generator's class path. It recreates its output directory before every run, so a type that
 * the schema no longer has leaves no stale source, and it runs the generator in a worker with its own class loader, so that the
 * generator and its dependencies stay off the build's class path.
 */
@CacheableTask
public abstract class GenerateGraphQLTask extends DefaultTask {

    /**
     * The schema directories. The worker selects the schema files in them with the options {@code includes} and {@code excludes},
     * which are part of {@link #getOptions()}, so the selection uses the generator's own globs.
     *
     * @return the schema directories
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSchemaDirectories();

    /**
     * The options in their string form, the input of {@code Options.fromMap}.
     *
     * @return the options
     */
    @Input
    public abstract MapProperty<String, String> getOptions();

    /**
     * The class path of the generator.
     *
     * @return the class path
     */
    @Classpath
    public abstract ConfigurableFileCollection getGeneratorClasspath();

    /**
     * The directory that receives the generated sources.
     *
     * @return the directory
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    /** Recreates the output directory and runs the generator in an isolated worker. */
    @TaskAction
    public void generate() {
        File output = getOutputDirectory().get().getAsFile();
        getFileSystemOperations().delete(spec -> spec.delete(output));
        try {
            Files.createDirectories(output.toPath());
        } catch (IOException e) {
            throw new GradleException("Cannot create the output directory " + output, e);
        }
        List<File> schemaDirectories = new TreeSet<>(getSchemaDirectories().getFiles()).stream().filter(File::isDirectory).toList();
        if (schemaDirectories.isEmpty()) {
            throw new GradleException("No GraphQL schema directory found. Put the schema in src/main/resources/graphql or set graphqlTools.schemaDirs.");
        }
        WorkQueue queue = getWorkerExecutor().classLoaderIsolation(spec -> spec.getClasspath().from(getGeneratorClasspath()));
        queue.submit(GenerateAction.class, parameters -> {
            parameters.getSchemaDirectories().setFrom(schemaDirectories);
            parameters.getOutputDirectory().set(getOutputDirectory());
            parameters.getOptions().set(getOptions());
        });
        queue.await();
    }
}
