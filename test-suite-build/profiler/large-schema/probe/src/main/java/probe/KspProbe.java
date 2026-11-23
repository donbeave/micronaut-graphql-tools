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
package probe;

import com.google.devtools.ksp.processing.Resolver;
import com.google.devtools.ksp.processing.SymbolProcessor;
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment;
import com.google.devtools.ksp.processing.SymbolProcessorProvider;
import com.google.devtools.ksp.symbol.KSAnnotated;

import java.util.List;

/** The KSP host: the workload runs in the first round of the processor. */
public final class KspProbe implements SymbolProcessorProvider {

    @Override
    public SymbolProcessor create(SymbolProcessorEnvironment environment) {
        return new SymbolProcessor() {
            @Override
            public List<KSAnnotated> process(Resolver resolver) {
                Workload.runOnce("ksp");
                return List.of();
            }

            @Override
            public void finish() {
            }

            @Override
            public void onError() {
            }
        };
    }
}
