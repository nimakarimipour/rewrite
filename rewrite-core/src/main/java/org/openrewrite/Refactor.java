/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import org.openrewrite.internal.lang.NonNullApi;
import org.openrewrite.internal.lang.Nullable;

import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

/**
 * A refactoring operation to be executed on a set of source files involving
 * one or more top-level refactoring visitors.
 */
@NonNullApi
public class Refactor {
    private MeterRegistry meterRegistry = Metrics.globalRegistry;

    @Getter
    private final Collection<RefactorVisitor<? extends Tree>> visitors = new ArrayList<>();

    @SafeVarargs
    public final Refactor visit(RefactorVisitor<? extends Tree>... visitors) {
        Collections.addAll(this.visitors, visitors);
        return this;
    }

    public final Refactor visit(Iterable<RefactorVisitor<? extends Tree>> visitors) {
        visitors.forEach(this.visitors::add);
        return this;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public <S extends SourceFile> S fixed(S tree) {
        return (S) fix(Collections.singletonList(tree)).iterator().next().getFixed();
    }

    /**
     * Generate a change set by visiting a collection of sources.
     *
     * @param sources The collection of sources don't have to have the same type. They can be a mixture of, for example,
     *                Java source files and Maven POMs.
     * @return A change set.
     */
    public Collection<Change> fix(Iterable<? extends SourceFile> sources) {
        return fix(sources, 3);
    }

    /**
     * Generate a change set by visiting a collection of sources.
     *
     * @param sources   The collection of sources don't have to have the same type. They can be a mixture of, for example,
     *                  Java source files and Maven POMs.
     * @param maxCycles The maximum number of iterations to visit the files.
     * @return A change set.
     */
    public Collection<Change> fix(Iterable<? extends SourceFile> sources, int maxCycles) {
        Timer.Sample sample = Timer.start();

        Map<SourceFile, Change> changesByTree = new HashMap<>();

        List<SourceFile> accumulatedSources = new ArrayList<>();
        sources.forEach(accumulatedSources::add);

        for (int i = 0; i < maxCycles; i++) {
            int rulesThatMadeChangesThisCycle = 0;
            for (int j = 0; j < accumulatedSources.size(); j++) {
                SourceFile prev = accumulatedSources.get(j);
                if (prev == null) {
                    // source was deleted in a previous iteration
                    continue;
                }

                SourceFile acc = prev;

                for (RefactorVisitor<? extends Tree> visitor : visitors) {
                    try {
                        visitor.next();

                        if (!visitor.isIdempotent() && i > 0) {
                            continue;
                        }

                        SourceFile before = acc;
                        acc = (SourceFile) transformPipeline(acc, visitor);

                        if (before != acc) {
                            // we should only report on the top-level visitors, not any andThen() visitors that
                            // are applied as part of the top-level visitor's pipeline
                            changesByTree.compute(acc, (acc2, prevChange) -> prevChange == null ?
                                    new Change(prev, acc2, Collections.singleton(visitor.getName())) :
                                    new Change(prev, acc2, Stream
                                            .concat(prevChange.getRulesThatMadeChanges().stream(), Stream.of(visitor.getName()))
                                            .collect(toSet()))
                            );
                            rulesThatMadeChangesThisCycle++;
                        }
                    } catch (Throwable t) {
                        Counter.builder("rewrite.visitor.errors")
                                .baseUnit("errors")
                                .description("Visitors that threw exceptions")
                                .tag("visitor", visitor.getClass().getName())
                                .tag("tree.type", prev.getClass().getName())
                                .register(meterRegistry)
                                .increment();
                    }
                }

                // we've seen all the files once, so if any new source files needs to be generated by any of the visitors,
                // let's do that now. On the next cycle, these visitors shouldn't generate these files again, but update
                // them in place as necessary.
                for (RefactorVisitor<? extends Tree> visitor : visitors) {
                    rulesThatMadeChangesThisCycle += visitor.generate().stream()
                            .map(g -> accumulatedSources.add((SourceFile) g))
                            .count();
                }

                accumulatedSources.set(j, acc);
            }

            if (rulesThatMadeChangesThisCycle == 0) {
                break;
            }
        }

        sample.stop(Timer.builder("rewrite.refactor.plan")
                .description("The time it takes to execute a refactoring plan consisting of potentially more than one visitor over more than one cycle")
                .tag("outcome", changesByTree.isEmpty() ? "unchanged" : "changed")
                .register(meterRegistry));

        for (Change change : changesByTree.values()) {
            for (String ruleThatMadeChange : change.getRulesThatMadeChanges()) {
                Counter.builder("rewrite.refactor.plan.changes")
                        .description("The number of changes requested by a visitor")
                        .tag("visitor", ruleThatMadeChange)
                        .tag("tree.type", change.getTreeType() == null ? "unknown" : change.getTreeType().getName())
                        .register(meterRegistry)
                        .increment();
            }
        }

        return changesByTree.values();
    }

    private Tree transformPipeline(Tree acc, RefactorVisitor<? extends Tree> visitor) {
        // by transforming the AST for each op, we allow for the possibility of overlapping changes
        Timer.Sample sample = Timer.start();
        acc = visitor.visit(acc);
        for (RefactorVisitor<? extends Tree> vis : visitor.andThen()) {
            acc = transformPipeline(acc, vis);
        }

        sample.stop(Timer.builder("rewrite.refactor.visit")
                .description("The time it takes to visit a single AST with a particular refactoring visitor and its pipeline")
                .tag("visitor", visitor.getClass().getName())
                .tags(visitor.getTags())
                .tag("tree.type", acc.getClass().getSimpleName())
                .register(meterRegistry));

        return acc;
    }

    public Refactor setMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        return this;
    }
}
