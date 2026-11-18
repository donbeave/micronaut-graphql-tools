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
 * The compile-time backend: compiles the case again with the compiler of its language, Micronaut's processing and the visitor
 * of this module with the checks on, and reads the findings from what the compiler reports: an error of the form
 * {@code [code] coordinate: sentence} with the schema location on a line of its own, or a warning of the same form.
 * It is the second of the two compilations of every case: the first one (the runner's, {@code checks=false}) has to succeed,
 * and this one has to fail with the findings of {@code compile.txt} and with nothing else, or succeed where {@code compile.txt}
 * is {@code ok}. Java and Groovy compile in process; Kotlin has no backend here until #143.
 */
@CompileStatic
final class VisitorCompileTimeBinder implements CompileTimeBinder {

    @Override
    String getName() {
        return 'compile-time (visitor)'
    }

    @Override
    Verdict bind(BindRequest request) {
        if (!(request.compiler instanceof CheckedCaseCompiler)) {
            return Verdict.unavailable()
        }
        String label = "${request.corpusCase.name} [${request.language.dir}]"
        CompiledCase checked = ((CheckedCaseCompiler) request.compiler).compileChecked(request.corpusCase, request.work.resolve('checked'))
        List<Finding> findings = []
        List<String> others = []
        checked.errors.each { String error ->
            Finding finding = parse(error, false)
            if (finding == null) {
                others.add(error)
            } else {
                findings.add(finding)
            }
        }
        if (!others.empty) {
            throw new AssertionError("${label}: the checked compilation reports errors that are not findings:\n  ${others.join('\n  ')}".toString())
        }
        if (checked.success != findings.empty) {
            throw new AssertionError("${label}: the checked compilation ${checked.success ? 'succeeds' : 'fails'} although it reports ${findings*.line()}".toString())
        }
        checked.warnings.each { String warning ->
            Finding finding = parse(warning, true)
            if (finding != null) {
                findings.add(finding)
            }
        }
        return findings.empty ? Verdict.bound() : Verdict.failed(findings)
    }

    /** {@code Q.java:3: error: [code] coordinate: ...} is how javac prints a diagnostic: the message starts after the kind. */
    private static Finding parse(String diagnostic, boolean warning) {
        List<String> lines = diagnostic.readLines()
        int at = lines.findIndexOf { it.contains('[') && it =~ /\[[a-z-]+\] \S+: / }
        if (at < 0) {
            return null
        }
        java.util.regex.Matcher head = (lines[at] =~ /\[([a-z-]+)\] (\S+): /)
        head.find()
        String code = head.group(1)
        String coordinate = head.group(2)
        String location = '-'
        lines.each { String line ->
            java.util.regex.Matcher schema = (line =~ /^\s+schema (\S+):(\d+):(\d+)$/)
            if (schema.find()) {
                location = "${Path.of(schema.group(1)).fileName}:${schema.group(2)}:${schema.group(3)}".toString()
            }
        }
        return new Finding(code, coordinate, location, warning)
    }

}
