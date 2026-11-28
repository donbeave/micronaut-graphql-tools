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
package io.micronaut.graphql.tools.codegen.ir.client;

import java.util.List;

/**
 * A selection set of the assembled document: its fields, inline fragments and fragment spreads, each in the order of the document.
 * The order of the selections among the three lists is not kept.
 *
 * @param fields          the selected fields
 * @param inlineFragments the inline fragments
 * @param fragmentSpreads the fragment spreads
 */
public record SelectionSet(List<SelectedField> fields, List<InlineFragment> inlineFragments, List<FragmentSpread> fragmentSpreads) {

    /**
     * Creates a selection set, keeping immutable copies of the lists.
     */
    public SelectionSet {
        fields = List.copyOf(fields);
        inlineFragments = List.copyOf(inlineFragments);
        fragmentSpreads = List.copyOf(fragmentSpreads);
    }
}
