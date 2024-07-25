/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.core.inference;

import org.elasticsearch.TransportVersion;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.inference.TaskType;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xpack.core.ml.AbstractBWCWireSerializationTestCase;

import java.io.IOException;
import java.time.Instant;

import static org.hamcrest.Matchers.is;

public class InferenceRequestStatsTests extends AbstractBWCWireSerializationTestCase<InferenceRequestStats> {
    private static final Instant timestamp = Instant.ofEpochMilli(1721946187204L);

    public static InferenceRequestStats createRandom() {
        var modelId = randomBoolean() ? randomAlphaOfLength(10) : null;

        return new InferenceRequestStats(randomAlphaOfLength(10), randomFrom(TaskType.values()), modelId, Instant.now(), randomInt());
    }

    public void testToXContent_DoesNotWriteModelId_WhenItIsNull() throws IOException {
        var stats = new InferenceRequestStats("service", TaskType.TEXT_EMBEDDING, null, timestamp, 1);

        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        stats.toXContent(builder, null);
        String xContentResult = Strings.toString(builder);

        assertThat(xContentResult, is("""
            {"service":"service","task_type":"text_embedding","count":1,"@timestamp":1721946187204}"""));
    }

    public void testToXContent_WritesModelId_WhenItIsDefined() throws IOException {
        var stats = new InferenceRequestStats("service", TaskType.TEXT_EMBEDDING, "model_id", timestamp, 2);

        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        stats.toXContent(builder, null);
        String xContentResult = Strings.toString(builder);

        assertThat(xContentResult, is("""
            {"service":"service","task_type":"text_embedding","count":2,"@timestamp":1721946187204,"model_id":"model_id"}"""));
    }

    @Override
    protected InferenceRequestStats mutateInstanceForVersion(InferenceRequestStats instance, TransportVersion version) {
        return instance;
    }

    @Override
    protected Writeable.Reader<InferenceRequestStats> instanceReader() {
        return InferenceRequestStats::new;
    }

    @Override
    protected InferenceRequestStats createTestInstance() {
        return createRandom();
    }

    @Override
    protected InferenceRequestStats mutateInstance(InferenceRequestStats instance) throws IOException {
        return randomValueOtherThan(instance, this::createTestInstance);
    }
}
