package io.micronaut.graphql.tools.visitor

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/** The schema of a compilation is read lazily, once, and what fails to load is kept for one report. */
class CheckedSchemaSpec extends Specification {

    @TempDir
    Path dir

    def "nothing is read until the schema is asked for, and it is read once however often it is asked for"() {
        given:
        Files.writeString(dir.resolve('schema.graphqls'), 'type Query { ping: String }\n')
        def schema = new CheckedSchema([dir])

        expect:
        schema.loads() == 0

        when:
        def first = schema.model()
        def second = schema.model()

        then:
        first.present
        second.is(first) || second.get().is(first.get())
        schema.loads() == 1
        schema.files() == 1
        schema.problems().isEmpty()
    }

    def "a schema that cannot be read is reported once, with the absolute path of the file, and is not retried"() {
        given:
        Files.writeString(dir.resolve('schema.graphqls'), 'type Query { ping: }\n')
        def schema = new CheckedSchema([dir])

        when:
        def model = schema.model()
        schema.model()

        then:
        model.empty
        schema.loads() == 1
        schema.problems()*.code() == ['schema-invalid']
        schema.problems()[0].sentence().contains(dir.resolve('schema.graphqls').toString())
    }

    def "the directories that do not exist are found without reading anything"() {
        given:
        def schema = new CheckedSchema([dir, dir.resolve('missing')])

        expect:
        schema.missingDirectories()*.code() == ['processor-schema-not-found']
        schema.loads() == 0
    }
}
