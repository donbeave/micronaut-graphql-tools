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

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.stream.Stream

/** Compiles the client interfaces of one language of a case with the client visitor on the compiler path. */
@CompileStatic
interface ClientCaseCompiler {

    Language getLanguage()

    ClientCompiled compile(ClientCorpusCase corpusCase, Path work)

    /**
     * The directories and the options of the compilation of a case, as a build passes them: the client directory and the schema
     * directory are copies under {@code work}, so that a position in a message is under a directory of this run.
     */
    @CompileStatic
    static final class Setup {

        static final String PREFIX = 'micronaut.graphql.tools.processor.'
        static final String CLIENT_DIRS = PREFIX + 'client.dirs'
        static final String SCHEMA_DIRS = PREFIX + 'schema.dirs'

        final Path clientDir
        final Path schemaDir
        final Map<String, String> options = new LinkedHashMap<>()

        Setup(ClientCorpusCase corpusCase, Path work) {
            Path client = null
            switch (corpusCase.clientDirs) {
                case 'case':
                    if (Files.isDirectory(corpusCase.clientDir())) {
                        client = copy(corpusCase.clientDir(), work.resolve('client'))
                        options.put(CLIENT_DIRS, client.toString())
                    }
                    break
                case 'missing':
                    client = work.resolve('missing-client')
                    options.put(CLIENT_DIRS, client.toString())
                    break
                default:
                    break
            }
            Path schema = null
            if (corpusCase.schemaDirs == 'case' && Files.isDirectory(corpusCase.schemaDir())) {
                schema = copy(corpusCase.schemaDir(), work.resolve('schema'))
                options.put(SCHEMA_DIRS, schema.toString())
            }
            options.putAll(corpusCase.options)
            this.clientDir = client
            this.schemaDir = schema
        }

        private static Path copy(Path from, Path to) {
            try (Stream<Path> files = Files.walk(from)) {
                files.forEach { Path file ->
                    Path target = to.resolve(from.relativize(file).toString())
                    if (Files.isDirectory(file)) {
                        Files.createDirectories(target)
                    } else {
                        Files.createDirectories(target.parent)
                        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
            return to
        }

    }

}
