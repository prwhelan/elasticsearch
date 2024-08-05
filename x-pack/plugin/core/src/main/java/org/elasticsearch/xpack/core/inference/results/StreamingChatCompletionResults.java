/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.core.inference.results;

import org.elasticsearch.common.collect.Iterators;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.ChunkedToXContent;
import org.elasticsearch.common.xcontent.ChunkedToXContentHelper;
import org.elasticsearch.inference.InferenceResults;
import org.elasticsearch.inference.InferenceServiceResults;
import org.elasticsearch.inference.TaskType;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Flow;

public class StreamingChatCompletionResults implements InferenceServiceResults {
    public static final String NAME = "streaming_chat_completion_service_results";
    public static final String COMPLETION = TaskType.COMPLETION.name().toLowerCase(Locale.ROOT);
    private final boolean isFinishedStreaming;
    private final List<Result> delta;
    private Flow.Subscriber<? super InferenceServiceResults> subscriber;

    public StreamingChatCompletionResults(boolean isFinishedStreaming, List<Result> delta) {
        this.isFinishedStreaming = isFinishedStreaming;
        this.delta = delta;
    }

    @Override
    public List<? extends InferenceResults> transformToCoordinationFormat() {
        return delta; //TODO
    }

    @Override
    public List<? extends InferenceResults> transformToLegacyFormat() {
        return delta; //TODO
    }

    @Override
    public Map<String, Object> asMap() {
        return Map.of(COMPLETION, delta);
    }

    @Override
    public boolean isStreaming() {
        return true;
    }

    @Override
    public boolean isFinishedStreaming() {
        if(isFinishedStreaming) {
            Map.of();
        }
        return isFinishedStreaming;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super InferenceServiceResults> subscriber) {
        this.subscriber = subscriber;
    }

    public void publish(boolean isFinishedStreaming, List<Result> results) {
        this.subscriber.onNext(new StreamingChatCompletionResults(isFinishedStreaming, results));
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeCollection(delta);
    }

    @Override
    public Iterator<? extends ToXContent> toXContentChunked(ToXContent.Params params) {
        return ChunkedToXContentHelper.array(COMPLETION, delta.iterator());
    }

    public record Result(String delta) implements InferenceResults, Writeable, ChunkedToXContent {

        public static final String RESULT = "delta";

        public Result(StreamInput in) throws IOException {
            this(in.readString());
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeString(delta);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject();
            builder.field(RESULT, delta);
            builder.endObject();

            return builder;
        }

        @Override
        public Iterator<? extends ToXContent> toXContentChunked(Params params) {
            return Iterators.concat(
                ChunkedToXContentHelper.startObject(),
                ChunkedToXContentHelper.field(RESULT, delta),
                ChunkedToXContentHelper.endObject()
            );
        }

        @Override
        public String getResultsField() {
            return RESULT;
        }

        @Override
        public Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put(RESULT, delta);
            return map;
        }

        @Override
        public Map<String, Object> asMap(String outputField) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put(outputField, delta);
            return map;
        }

        @Override
        public Object predictedValue() {
            return delta;
        }

        @Override
        public String getWriteableName() {
            return NAME;
        }

        @Override
        public boolean isFragment() {
            return InferenceResults.super.isFragment();
        }
    }
}
