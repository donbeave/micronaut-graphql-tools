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
import io.micronaut.graphql.tools.visitor.ClientMetadataLoader

import java.util.regex.Matcher

/**
 * What a client case says, as the lines of the expected files. A finding is {@code <code> <member> <position>}: the code and the
 * member of the first line of the message, and the position of its {@code document} or {@code schema} line relative to the
 * client directory ({@code <client>}) or the schema directory ({@code <schema>}), or {@code -}. An operation is
 * {@code <client>#<method> type=.. name=.. payload=.. variables=.. document=..}, with the whitespace of the document removed:
 * the printer of graphql-java spaces a compact document a little differently from one release to the next.
 */
@CompileStatic
final class ClientVerdicts {

    private ClientVerdicts() {
    }

    /** @return {@code ok}, or the sorted findings of the errors; an error that is not a finding is listed as {@code UNEXPECTED} */
    static String compile(ClientCompiled compiled) {
        if (compiled.success && compiled.errors.empty) {
            return 'ok'
        }
        List<String> lines = compiled.errors.collectMany { String error -> findings(error, compiled) }.sort()
        return lines.empty ? 'UNEXPECTED the compilation failed without a message' : lines.join('\n')
    }

    // A message can carry several findings (the Groovy compiler reports its errors in one text, with a header), and a finding runs over the lines
    // that follow its first line, up to the next first line.
    private static List<String> findings(String error, ClientCompiled compiled) {
        List<String> found = []
        String code = null
        String member = null
        String position = '-'
        boolean any = false
        error.readLines().each { String line ->
            Matcher first = (line =~ /\[([a-z]+(?:-[a-z]+)*)\] (\S+?):(?: |$)/)
            Matcher at = (line =~ /^\s+(?:document|schema) (.+)$/)
            if (first.find()) {
                if (code != null) {
                    found.add("${code} ${member} ${position}".toString())
                }
                code = first.group(1)
                member = first.group(2)
                position = '-'
                any = true
            } else if (code != null && at.find()) {
                position = relative(at.group(1), compiled)
            }
        }
        if (code != null) {
            found.add("${code} ${member} ${position}".toString())
        }
        return any ? found : ['UNEXPECTED ' + error.readLines().find { !it.isBlank() }.take(160)]
    }

    private static String relative(String position, ClientCompiled compiled) {
        String result = position
        if (compiled.clientDir != null) {
            result = result.replace(compiled.clientDir.toString(), '<client>')
        }
        if (compiled.schemaDir != null) {
            result = result.replace(compiled.schemaDir.toString(), '<schema>')
        }
        return result
    }

    /** @return one line per operation method of the given client interfaces, sorted */
    static String metadata(ClientCompiled compiled, List<String> clients) {
        List<String> lines = []
        clients.each { String client ->
            Map<String, Map<String, Object>> methods = ClientMetadataLoader.read(compiled.output.collect { it.toFile() }, client)
            methods.each { String method, Map<String, Object> values ->
                List<String> variables = (List<String>) values.variables
                lines.add(("${client.substring(client.lastIndexOf('.') + 1)}#${method} type=${values.operationType} name=${values.operationName ?: '-'} " +
                        "payload=${values.payload} variables=${variables.empty ? '-' : variables.join(',')} " +
                        "document=${((String) values.document).replaceAll(/\s+/, '')}").toString())
            }
        }
        return lines.sort().join('\n')
    }

}
