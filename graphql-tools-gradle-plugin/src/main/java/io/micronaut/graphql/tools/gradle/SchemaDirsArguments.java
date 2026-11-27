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

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.CommandLineArgumentProvider;

import java.util.List;

/**
 * The {@code -A} options of the compile-time processor for {@code javac}: the schema directories, which are an input of the
 * compile task so that a schema edit reruns it, and the module directory.
 */
public abstract class SchemaDirsArguments implements CommandLineArgumentProvider {

    /**
     * The schema directories.
     *
     * @return the directories
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getDirs();

    /**
     * The project directory of the module that sets the schema directories.
     *
     * @return the directory
     */
    @Internal
    public abstract DirectoryProperty getModuleDir();

    @Override
    public Iterable<String> asArguments() {
        return List.of(
                "-A" + CompileTimeWiring.SCHEMA_DIRS + "=" + CompileTimeWiring.joined(getDirs().getFiles()),
                "-A" + CompileTimeWiring.MODULE_DIR + "=" + getModuleDir().get().getAsFile().getAbsolutePath());
    }
}
