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
package io.micronaut.graphql.tools.corpus

import groovy.transform.CompileStatic

/**
 * A binder backend of the corpus: something that says, for one case in one language, whether the schema binds and,
 * if not, with which findings. There are two: the {@link RuntimeBinder} (the real application context, working now)
 * and the {@link CompileTimeBinder} (the visitor of #150, a stub until then). The runner compares them.
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
