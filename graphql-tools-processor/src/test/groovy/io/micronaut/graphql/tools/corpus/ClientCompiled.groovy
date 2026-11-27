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
package io.micronaut.graphql.tools.corpus

import groovy.transform.CompileStatic

import java.nio.file.Path

/**
 * The outcome of compiling one language of a client case: the errors of the compiler, and the directories that hold the output
 * (for the metadata that the visitor wrote) and the client and schema directories of the run (so that a position names a path
 * relative to them).
 */
@CompileStatic
final class ClientCompiled {

    final boolean success
    final List<String> errors
    /** the class and resource directories of the output, for starting an application on it */
    final List<Path> output
    /** the path of the client directory of the run, or null when the run had none */
    final Path clientDir
    /** the path of the schema directory of the run, or null when the run had none */
    final Path schemaDir

    ClientCompiled(boolean success, List<String> errors, List<Path> output, Path clientDir, Path schemaDir) {
        this.success = success
        this.errors = errors
        this.output = output
        this.clientDir = clientDir
        this.schemaDir = schemaDir
    }

}
