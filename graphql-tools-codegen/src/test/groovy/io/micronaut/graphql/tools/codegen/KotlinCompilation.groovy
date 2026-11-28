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
package io.micronaut.graphql.tools.codegen

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Compiles Kotlin sources against compiled Java classes, in a process of its own: the Kotlin compiler (the system property
 * {@code codegen.kotlin.compiler}) does not share a class path with the tests. JSpecify annotations are errors, so a nullable type
 * that a caller uses as non-null fails the compilation.
 */
class KotlinCompilation {

    static final String NULLABILITY = '-Xnullability-annotations=@org.jspecify.annotations:strict'

    static Result compile(Path work, Map<String, String> sources, Path javaClasses) {
        Path src = Files.createDirectories(work.resolve('kotlin-sources'))
        Path out = Files.createDirectories(work.resolve('kotlin-classes'))
        List<String> files = []
        sources.each { path, content ->
            Path file = src.resolve(path)
            Files.createDirectories(file.parent)
            Files.writeString(file, content)
            files << file.toString()
        }
        List<String> compiler = System.getProperty('codegen.kotlin.compiler').split(File.pathSeparator) as List<String>
        List<String> libraries = compiler.findAll { new File(it).name.startsWith('kotlin-stdlib') }
        String classPath = ([javaClasses.toString()] + System.getProperty('codegen.client.classpath').split(File.pathSeparator).toList() + libraries)
                .join(File.pathSeparator)
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').toString(), '-cp', compiler.join(File.pathSeparator),
                                'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-jvm-target', '17', '-nowarn',
                                NULLABILITY, '-cp', classPath, '-d', out.toString()] + files
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start()
        String output = process.inputStream.text
        boolean finished = process.waitFor(5, TimeUnit.MINUTES)
        assert finished: 'the Kotlin compiler did not finish'
        new Result(process.exitValue() == 0, output, out)
    }

    static class Result {

        final boolean success
        final String output
        final Path classes

        Result(boolean success, String output, Path classes) {
            this.success = success
            this.output = output
            this.classes = classes
        }
    }
}
