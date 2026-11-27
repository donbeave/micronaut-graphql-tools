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
package io.micronaut.graphql.tools.codegen.golden;

import groovy.lang.GroovyClassLoader;
import io.micronaut.graphql.tools.codegen.GeneratedFile;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;

import java.io.IOException;
import java.util.List;

/**
 * The groovyc on the class path of the tests: all {@code .groovy} files are compiled together to classes in memory.
 */
public final class GroovyInMemoryCompiler implements InMemoryCompiler {

    @Override
    public List<String> compile(List<GeneratedFile> files) {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setTargetDirectory((String) null);
        try (GroovyClassLoader loader = new GroovyClassLoader(getClass().getClassLoader(), configuration)) {
            CompilationUnit unit = new CompilationUnit(configuration, null, loader);
            for (GeneratedFile file : files) {
                if (file.path().endsWith(".groovy")) {
                    unit.addSource(file.path(), file.content());
                }
            }
            unit.compile(Phases.CLASS_GENERATION);
            return List.of();
        } catch (CompilationFailedException e) {
            return List.of(e.getMessage());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
