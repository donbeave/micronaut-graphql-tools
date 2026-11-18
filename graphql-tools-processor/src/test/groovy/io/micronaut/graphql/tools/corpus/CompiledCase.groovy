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
 * The outcome of compiling one language of a case: a class output that can be started, or the errors of a source that
 * does not compile (a mistake in the case, reported by the runner).
 */
@CompileStatic
final class CompiledCase {

    final boolean success
    final Path classes
    final List<String> errors
    /** further class and resource directories of the output, for a build that writes them apart (Kotlin with KSP) */
    final List<Path> more
    /** the warnings of the compiler, which only a checked compilation is read for */
    final List<String> warnings

    CompiledCase(boolean success, Path classes, List<String> errors, List<Path> more = [], List<String> warnings = []) {
        this.success = success
        this.classes = classes
        this.errors = errors
        this.more = more
        this.warnings = warnings
    }

}
