/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.telemetry;

import org.elasticsearch.telemetry.metric.LongCounter;
import org.elasticsearch.telemetry.metric.MeterRegistry;

import java.util.Map;

public class APMInferenceMetrics implements InferenceMetrics {
    private final LongCounter inferenceRequests;

    private APMInferenceMetrics(LongCounter inferenceRequests) {
        this.inferenceRequests = inferenceRequests;
    }

    @Override
    public void countInferenceRequest(String service, String model) {
        try {
            inferenceRequests.incrementBy(1, Map.of("service", service, "model", model));
        } catch (Exception e) {
            // don't break anything trying to record metrics
        }
    }

    public static APMInferenceMetrics create(MeterRegistry meterRegistry) {
        var inferenceRequests = meterRegistry.registerLongCounter(
            "es.ml.inference.requests.total",
            "Number of inference requests for a given service and model",
            "count"
        );
        return new APMInferenceMetrics(inferenceRequests);
    }
}
