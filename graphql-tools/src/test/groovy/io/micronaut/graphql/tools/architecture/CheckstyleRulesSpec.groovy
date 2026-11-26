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
package io.micronaut.graphql.tools.architecture

import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern

/**
 * The Checkstyle rule that flags a mis-indented member declaration: the pattern is read from the configuration the build
 * runs, and is applied to declarations that the rule must flag and to ones it must leave alone.
 */
class CheckstyleRulesSpec extends Specification {

    @Shared Pattern pattern = modifierPattern()

    void "a constructor off the four-space grid or with several spaces after the modifier is flagged"() {
        expect:
        pattern.matcher(line).find()

        where:
        line << [
                '  public   GraphQLBeanIntrospectionRegistry() {',
                '  public Registry() {',
                '   private final Map<String, String> names;',
                '    public   Registry() {',
                '    protected  void run() {',
        ]
    }

    void "well formed declarations, continuation lines and comments are not flagged"() {
        expect:
        !pattern.matcher(line).find()

        where:
        line << [
                '    public Registry() {',
                '    private final Map<String, String> names;',
                '        private int count;',
                '     * a public API with a javadoc line',
                '                                     CheckPolicy checkPolicy) {',
                'public final class Registry {',
        ]
    }

    private static Pattern modifierPattern() {
        Path file = Paths.get(System.getProperty('docs.root')).resolve('config/checkstyle/checkstyle.xml')
        def found = (Files.readString(file) =~ /<module name="RegexpSingleline">\s*<property name="format" value="([^"]*\(public\|[^"]*)"/)
        assert found.find(): 'checkstyle.xml has no RegexpSingleline rule'
        Pattern.compile(found.group(1), Pattern.MULTILINE)
    }
}
