/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.core.inference;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.inference.TaskType;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;

public class InferenceRequestStats implements SerializableStats {
    private final InferenceFeatureSetUsage.ModelStats modelStats;
    private final String modelId;
    private final Instant timestamp;

    public InferenceRequestStats(String service, TaskType taskType, @Nullable String modelId, Instant timestamp, long count) {
        this.modelStats = new InferenceFeatureSetUsage.ModelStats(service, taskType, count);
        this.modelId = modelId;
        this.timestamp = timestamp;
    }

    public InferenceRequestStats(StreamInput in) throws IOException {
        this.modelStats = new InferenceFeatureSetUsage.ModelStats(in);
        this.timestamp = in.readInstant();
        this.modelId = in.readOptionalString();
    }

    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        builder.field("service", modelStats.service());
        builder.field("task_type", modelStats.taskType().toString());
        builder.field("count", modelStats.count());
        builder.field("@timestamp", timestamp.toEpochMilli());

        if (modelId != null) {
            builder.field("model_id", modelId);
        }

        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        modelStats.writeTo(out);
        out.writeInstant(timestamp);
        out.writeOptionalString(modelId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InferenceRequestStats that = (InferenceRequestStats) o;
        return Objects.equals(modelStats, that.modelStats)
            && Objects.equals(modelId, that.modelId)
            && Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelStats, modelId, timestamp);
    }
}
