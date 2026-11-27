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
package io.micronaut.graphql.tools.codegen;

import io.micronaut.graphql.tools.codegen.ir.Ir;

import java.util.List;

/**
 * Turns the intermediate representation into the sources of one language. An emitter makes no naming or mapping decision: it
 * only writes the syntax of the IR nodes, and it returns the files in path order. The shared interface is extracted with the
 * other emitters; this one is the seam that the Java emitter is built behind.
 */
interface Emitter {

    /**
     * Emits the files of an IR.
     *
     * @param ir      the intermediate representation
     * @param options the options that concern emission
     * @return the files, in path order
     */
    List<GeneratedFile> emit(Ir ir, Options options);
}
