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
package example.tck

import io.micronaut.graphql.tools.input.ArgumentValue
import spock.lang.Specification

class OptionalHelpersSpec extends Specification {

    void "only a provided non-null value is present"() {
        expect:
        OptionalHelpers.describePresence(ArgumentValue.omitted()) == 'absent'
        OptionalHelpers.describePresence(ArgumentValue.ofNullable(null)) == 'absent'
        OptionalHelpers.describePresence(ArgumentValue.ofNullable('a')) == 'present:a'

        when:
        def seen = []
        OptionalHelpers.rememberIfPresent(ArgumentValue.omitted(), seen)
        OptionalHelpers.rememberIfPresent(ArgumentValue.ofNullable(null), seen)
        OptionalHelpers.rememberIfPresent(ArgumentValue.ofNullable('a'), seen)

        then:
        seen == ['a']
    }
}
