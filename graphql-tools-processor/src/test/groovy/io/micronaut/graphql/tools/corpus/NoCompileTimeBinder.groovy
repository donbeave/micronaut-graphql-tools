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
