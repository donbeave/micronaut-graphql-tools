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
 * One compilation of a case in one language: the sources of a module. A case that does not split its sources has one unit with
 * no name; a case that does has one unit per {@code module-<name>} directory, and a unit sees the output of the units that sort
 * before it, as a module of a build sees the modules it depends on.
 */
@CompileStatic
final class SourceUnit {

    final String name
    final List<Path> sources

    SourceUnit(String name, List<Path> sources) {
        this.name = name
        this.sources = sources
    }

}
