package io.micronaut.graphql.tools.docs

import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The module map in README.md lists the Gradle projects of settings.gradle.kts, and every module folder has a README
 * with the six sections that CONTRIBUTING.md and the module READMEs describe.
 */
class ModuleReadmesSpec extends Specification {

    static final List<String> SECTIONS = [
            '## Purpose', '## Responsibility', '## Who uses it', '## Public and internal',
            '## Relation to other modules', '## Build and test'
    ]

    @Shared Path root = Paths.get(System.getProperty('docs.root'))

    def "the module map lists the projects of settings.gradle.kts, exactly"() {
        given:
        def included = (Files.readString(root.resolve('settings.gradle.kts')) =~ /(?m)^include\("([^"]+)"\)/)
                .collect { it[1] }
        def readme = Files.readString(root.resolve('README.md'))
        def mapped = (readme.split('\n## ').find { it.startsWith('Modules\n') } =~ /(?m)^\| \[`([^`]+)`]\(\1\/README\.md\) \|/)
                .collect { it[1] }

        expect:
        included.size() >= 9
        mapped.sort() == included.sort()
    }

    def "every module folder has a README with the six sections"() {
        given:
        def included = (Files.readString(root.resolve('settings.gradle.kts')) =~ /(?m)^include\("([^"]+)"\)/)
                .collect { it[1] }

        expect:
        included.every { name ->
            def readme = root.resolve(name).resolve('README.md')
            Files.isRegularFile(readme) && SECTIONS.every { section -> Files.readAllLines(readme).contains(section) }
        }
    }
}
