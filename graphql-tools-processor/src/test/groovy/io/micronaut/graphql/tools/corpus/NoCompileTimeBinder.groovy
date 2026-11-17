package io.micronaut.graphql.tools.corpus

import groovy.transform.CompileStatic

/**
 * The compile-time backend until #150: it cannot judge a case, so the runner asserts only the recorded expectations
 * of {@code compile.txt} against {@code runtime.txt}.
 */
@CompileStatic
final class NoCompileTimeBinder implements CompileTimeBinder {

    static final NoCompileTimeBinder INSTANCE = new NoCompileTimeBinder()

    private NoCompileTimeBinder() {
    }

    @Override
    String getName() {
        return 'compile-time (not available until #150)'
    }

    @Override
    Verdict bind(BindRequest request) {
        return Verdict.unavailable()
    }

}
