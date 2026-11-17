package example.data

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.micronaut.core.annotation.Introspected

@CompileStatic
@Introspected
@ToString(includePackage = false)
@EqualsAndHashCode(includes = 'id')
class Book {
    final String id
    final String title
    final Integer year

    Book(String id, String title, Integer year) {
        this.id = id
        this.title = title
        this.year = year
    }
}
