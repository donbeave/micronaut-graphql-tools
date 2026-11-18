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
package io.micronaut.graphql.tools.binding

import io.micronaut.graphql.tools.binding.internal.manifest.ManifestRenderer
import io.micronaut.graphql.tools.binding.internal.plan.BindingPlan
import io.micronaut.graphql.tools.binding.internal.plan.BindingPlan.Entry
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Matcher
import java.util.regex.Pattern

/** The examples of the format specification (the manifest guide page) are golden files: this reads them. */
class ManifestFormatSpec extends Specification {

    static final List<String> KINDS = ['object', 'interface', 'union', 'enum', 'input', 'scalar', 'root', 'resolver',
                                       'property', 'claimed', 'resolves', 'enumValue', 'inputField']

    static final Pattern HEADER = ~/(?s)# graphql-binding-manifest 2\n# binder: micronaut-graphql-tools (\S+)\n# graphql-java: (\S+)\n# schema-sha256: ([0-9a-f]{64})\n/

    static List<String> examples() {
        String adoc = Files.readString(Path.of(System.getProperty('manifest.spec')), StandardCharsets.UTF_8)
        Matcher blocks = Pattern.compile(/(?s)\[source,text\]\n----\n(# graphql-binding-manifest .*?)----\n/).matcher(adoc)
        List<String> result = []
        while (blocks.find()) {
            result << blocks.group(1)
        }
        result
    }

    void "the specification has examples"() {
        expect:
        examples().size() >= 2
    }

    void "an example is a manifest of the specified grammar, sorted, in ASCII, ended by LF"() {
        given:
        def lines = text.readLines().drop(4)

        expect:
        text.endsWith('\n')
        HEADER.matcher(text).lookingAt()
        text.every { (it as char) == ('\n' as char) || (it as char) >= (0x20 as char) && (it as char) <= (0x7e as char) }
        lines == new ArrayList<>(lines).sort(false) { a, b -> a.compareTo(b) }
        lines.every { line ->
            line.startsWith('!') ? line ==~ /![a-z]+(-[a-z]+)* \S.*/
                    : line.startsWith('~ignored ') ? line.length() > 9
                    : line ==~ /\S.* -> (${KINDS.join('|')}) ; \S.* ; \S.* ; \S.*/
        }

        where:
        text << examples()
    }

    void "an example is what the renderer writes for its own plan"() {
        given:
        def lines = text.readLines()
        def entries = []
        def diagnostics = []
        def ignored = []
        lines.drop(4).each { String line ->
            if (line.startsWith('!')) {
                int space = line.indexOf(' ')
                diagnostics << ManifestRenderer.diagnostic(line.substring(1, space),
                        line.substring(space + 1).split(' ; ').collect { decode(it) } as String[])
            } else if (line.startsWith('~ignored ')) {
                ignored << ManifestRenderer.ignored(decode(line.substring(9)))
            } else {
                def (subject, rest) = line.split(' -> ', 2) as List
                def (kind, member, declared, converted) = rest.split(' ; ', 4).collect { decode(it) }
                entries << new Entry(subject, kind, { member }, { declared }, { converted })
            }
        }
        def header = [lines[1], lines[2], lines[3]]

        when:
        def rendered = ManifestRenderer.render(new BindingPlan(entries, diagnostics, ignored), header)

        then:
        (rendered.header() + rendered.lines()).join('\n') + '\n' == text

        where:
        text << examples()
    }

    private static String decode(String part) {
        part.replaceAll(/\\u([0-9a-f]{4})/) { all, hex -> String.valueOf((char) Integer.parseInt(hex, 16)) }
    }
}
