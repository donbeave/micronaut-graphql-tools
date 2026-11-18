package example.tck

import io.micronaut.graphql.tools.ArgumentValue
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
