package io.micronaut.graphql.tools.corpus

import groovy.transform.CompileStatic

/**
 * A binder backend of the corpus: something that says, for one case in one language, whether the schema binds and,
 * if not, with which findings. There are two: the {@link RuntimeBinder} (the real application context, working now)
 * and the {@link CompileTimeBinder} (the visitor of #150, a stub until then). The runner compares them (DD-4).
 */
@CompileStatic
interface Binder {

    /** @return the name used in failure messages */
    String getName()

    /**
     * @param request the case, the language and what the runner compiled for it
     * @return the verdict, or {@link Verdict#unavailable()} when this backend cannot judge the case
     */
    Verdict bind(BindRequest request)

}
