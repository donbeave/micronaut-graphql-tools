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
package io.micronaut.graphql.tools.internal.wiring;

import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.internal.exceptions.AbstractMappingException;
import io.micronaut.graphql.tools.internal.exceptions.BindingFailuresException;
import io.micronaut.graphql.tools.internal.exceptions.UnexpectedBindingException;
import io.micronaut.graphql.tools.mapping.MappingContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The failures of the binding and the stack of steps that walks the schema, so that the walk needs no recursion. */
@Internal
final class BindingWalk {

    private final List<AbstractMappingException> bindingFailures = new ArrayList<>();

    // The walk from a field to the types it returns is a depth-first walk over the schema, so its depth is the length of
    // the longest chain of types: it runs from this stack of steps instead of recursing once per type.
    private final ArrayDeque<Step> steps = new ArrayDeque<>();

    private boolean draining;

    private Step running;

    private Step lastNested;

    void push(Runnable work, MappingContext context) {
        steps.push(new Step(work, context));
    }

    void addFailures(List<AbstractMappingException> failures) {
        bindingFailures.addAll(failures);
    }

    boolean collectFailure(MappingContext mappingContext, Runnable action) {
        try {
            action.run();
            return true;
        } catch (AbstractMappingException e) {
            bindingFailures.add(e);
        } catch (RuntimeException e) {
            bindingFailures.add(new UnexpectedBindingException(mappingContext, e));
        }
        return false;
    }

    /**
     * Runs the work as the recursive call it stands for: inside a running walk it becomes the next step, which runs
     * before the steps that were already waiting, so the order is the one of a recursive walk. A failure of the step
     * is reported on the field that reached it, as the recursive call's failure would have been.
     *
     * @param work the work of the nested call
     */
    void nested(Runnable work) {
        if (!draining) {
            drain(new Step(work, null));
            return;
        }
        Step step = new Step(work, running.context);
        steps.push(step);
        lastNested = step;
    }

    void drain(Step first) {
        draining = true;
        steps.push(first);
        try {
            while (!steps.isEmpty()) {
                Step step = steps.pop();
                if (step.cancelled) {
                    continue;
                }
                Step previous = running;
                running = step;
                try {
                    step.run();
                } finally {
                    running = previous;
                }
            }
        } finally {
            steps.clear();
            draining = false;
        }
    }

    /**
     * Runs the actions one after the other, each with the steps it starts, and stops at the first one that fails, as
     * a loop that calls them does.
     *
     * @param actions the actions to run in order
     */
    void inOrder(List<Runnable> actions) {
        inOrder(actions, 0);
    }

    void inOrder(List<Runnable> actions, int index) {
        if (index == actions.size()) {
            return;
        }
        Step rest = new Step(() -> inOrder(actions, index + 1), running.context);
        steps.push(rest);
        lastNested = null;
        try {
            actions.get(index).run();
        } catch (RuntimeException e) {
            rest.cancelled = true;
            throw e;
        }
        if (lastNested != null) {
            lastNested.after = rest;
        }
    }

    void throwBindingFailures() {
        if (bindingFailures.isEmpty()) {
            return;
        }
        if (bindingFailures.size() == 1) {
            throw bindingFailures.get(0);
        }
        List<AbstractMappingException> sorted = bindingFailures.stream()
                .sorted(Comparator.comparing(it -> it.getMappingContext().getCoordinate()))
                .toList();
        throw new BindingFailuresException(sorted);
    }

    final class Step {

        private final Runnable action;
        private final MappingContext context;
        private boolean cancelled;
        private Step after;

        private Step(Runnable action, MappingContext context) {
            this.action = action;
            this.context = context;
        }

        private void run() {
            if (context == null) {
                action.run();
            } else if (!collectFailure(context, action) && after != null) {
                after.cancelled = true;
            }
        }
    }
}
