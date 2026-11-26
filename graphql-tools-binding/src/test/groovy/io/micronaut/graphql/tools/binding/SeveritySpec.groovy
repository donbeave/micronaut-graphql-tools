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

import io.micronaut.graphql.tools.binding.internal.finding.DefaultSeverities
import io.micronaut.graphql.tools.binding.internal.finding.Severity
import spock.lang.Specification

class SeveritySpec extends Specification {

    void "the severities are fail, warn and ignore, read case-insensitively"() {
        expect:
        Severity.values()*.token() == ['fail', 'warn', 'ignore']
        Severity.fromToken(' WARN ') == Severity.WARN
        Severity.fromToken('maybe') == null
        Severity.fromToken(null) == null
    }

    void "every check code is in the default table at fail, and no failure code is"() {
        expect:
        DefaultSeverities.TABLE.size() == 10
        BindingCode.values().findAll { it.isCheck() }.every { DefaultSeverities.TABLE[it] == Severity.FAIL }
        BindingCode.values().findAll { !it.isCheck() }.every { !DefaultSeverities.TABLE.containsKey(it) }
    }

    void "strict false is warn for every check"() {
        expect:
        BindingCode.values().findAll { it.isCheck() }.every { DefaultSeverities.of(it, false) == Severity.WARN }
    }

    void "a failure code has no default severity"() {
        when:
        DefaultSeverities.of(BindingCode.FIELD_NOT_BOUND, true)

        then:
        thrown(IllegalArgumentException)
    }
}
