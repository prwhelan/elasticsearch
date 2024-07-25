/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.telemetry.index;

import org.elasticsearch.inference.Model;
import org.elasticsearch.xpack.core.inference.InferenceRequestStats;
import org.elasticsearch.xpack.inference.telemetry.InferenceStats;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class IndexBasedInferenceStats implements InferenceStats {
    private final Map<String, StatsCollector> counters = new ConcurrentHashMap<>();
    private final Supplier<Boolean> enabled;

    public IndexBasedInferenceStats(Supplier<Boolean> enabled) {
        this.enabled = enabled;
    }

    @Override
    public void incrementRequestCount(Model model) {
        if (enabled.get()) {
            counters.computeIfAbsent(key(model), key -> {
                var service = model.getConfigurations().getService();
                var taskType = model.getTaskType();
                var modelId = model.getServiceSettings().modelId();
                return new StatsCollector(
                    new LongAdder(),
                    (instant, counter) -> new InferenceRequestStats(service, taskType, modelId, instant, counter)
                );
            }).deltaCounter().increment();
        }
    }

    private String key(Model model) {
        StringBuilder builder = new StringBuilder();
        builder.append(model.getConfigurations().getService());
        builder.append(":");
        builder.append(model.getTaskType());

        if (model.getServiceSettings().modelId() != null) {
            builder.append(":");
            builder.append(model.getServiceSettings().modelId());
        }

        return builder.toString();
    }

    private record StatsCollector(LongAdder deltaCounter, BiFunction<Instant, Long, InferenceRequestStats> statsCreator) {
        private InferenceRequestStats finish() {
            return statsCreator.apply(Instant.now(), deltaCounter.sumThenReset());
        }
    }

    public List<InferenceRequestStats> drainMetrics() {
        var metrics = new ArrayList<InferenceRequestStats>(counters.size());
        var iterator = counters.keySet().iterator();
        while (iterator.hasNext()) {
            var stats = counters.remove(iterator.next());
            if (stats != null) {
                metrics.add(stats.finish());
            }
        }
        return metrics;
    }
}
