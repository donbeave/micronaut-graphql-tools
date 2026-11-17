package io.micronaut.graphql.tools.corpus

import groovy.transform.CompileStatic

/**
 * The languages of a case. A case has one source directory per language; the name of the directory is {@link #dir}.
 */
@CompileStatic
enum Language {

    JAVA('java', '.java'),
    GROOVY('groovy', '.groovy'),
    KOTLIN('kotlin', '.kt')

    final String dir
    final String extension

    Language(String dir, String extension) {
        this.dir = dir
        this.extension = extension
    }

}
