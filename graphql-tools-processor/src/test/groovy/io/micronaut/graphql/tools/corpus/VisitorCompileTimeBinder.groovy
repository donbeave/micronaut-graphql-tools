package io.micronaut.graphql.tools.corpus

import groovy.transform.CompileStatic

import java.nio.file.Path

/**
 * The compile-time backend for Java: compiles the case again with javac, Micronaut's processors and the visitor of this module
 * with the checks on, and reads the findings from the errors it reports, {@code [code] coordinate: sentence} with the schema
 * location on a line of its own. The other languages have no backend yet (#143), and say so.
 */
@CompileStatic
final class VisitorCompileTimeBinder implements CompileTimeBinder {

    @Override
    String getName() {
        return 'compile-time (visitor)'
    }

    @Override
    Verdict bind(BindRequest request) {
        if (request.language != Language.JAVA) {
            return Verdict.unavailable()
        }
        CompiledCase checked = ((JavaCaseCompiler) request.compiler).compileChecked(request.corpusCase, request.work.resolve('checked'))
        List<Finding> findings = []
        List<String> others = []
        checked.errors.each { String error ->
            Finding finding = parse(error)
            if (finding == null) {
                others.add(error)
            } else {
                findings.add(finding)
            }
        }
        if (!others.empty) {
            throw new AssertionError("${request.corpusCase.name} [java]: the checked compilation reports errors that are not findings:\n  ${others.join('\n  ')}".toString())
        }
        return findings.empty ? Verdict.bound() : Verdict.failed(findings)
    }

    /** {@code Q.java:3: error: [code] coordinate: ...} is how javac prints a diagnostic: the message starts after the kind. */
    private static Finding parse(String diagnostic) {
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
        return new Finding(code, coordinate, location)
    }

}
