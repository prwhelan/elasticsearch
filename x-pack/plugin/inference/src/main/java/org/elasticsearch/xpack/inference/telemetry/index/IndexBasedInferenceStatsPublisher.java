/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.telemetry.index;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.bulk.TransportBulkAction;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.internal.Client;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.ToXContentObject;
import org.elasticsearch.xcontent.XContentFactory;
import org.elasticsearch.xpack.core.ClientHelper;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class IndexBasedInferenceStatsPublisher {
    private static final Logger log = LogManager.getLogger(IndexBasedInferenceStatsPublisher.class);

    private final IndexBasedInferenceStats stats;
    private final BulkClient client;
    private final String indexName;

    public IndexBasedInferenceStatsPublisher(IndexBasedInferenceStats stats, Client client, String indexName) {
        this(stats, BulkClient.realClient(client), indexName);
    }

    IndexBasedInferenceStatsPublisher(IndexBasedInferenceStats stats, BulkClient client, String indexName) {
        this.stats = stats;
        this.client = client;
        this.indexName = indexName;
    }

    public void run(ActionListener<Void> onFinished) {
        try {
            publishAll(onFinished);
        } catch (Exception e) {
            onFinished.onFailure(e);
        }
    }

    private void publishAll(ActionListener<Void> onFinished) {
        var metrics = stats.drainMetrics();
        if (metrics.isEmpty()) {
            onFinished.onResponse(null);
            return;
        }

        var request = createBulkRequest(metrics);

        var bulkResponseListener = ActionListener.<BulkResponse>wrap(response -> {
            logFailedUploads(response);
            onFinished.onResponse(null);
        }, e -> {
            log.atDebug().withThrowable(e).log("Failed to upload metrics, discarding metrics.");
            onFinished.onFailure(e);
        });

        client.call(request, bulkResponseListener);
    }

    private BulkRequest createBulkRequest(List<? extends ToXContentObject> metrics) {
        var request = new BulkRequest();
        for (var stats : metrics) {
            try (var builder = stats.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS)) {
                request.add(new IndexRequest(indexName).opType(DocWriteRequest.OpType.CREATE).source(builder));
            } catch (IOException e) {
                // ignore failures
                log.atDebug().withThrowable(e).log("Failed to convert metrics into XContent, discarding metrics.");
            }
        }
        return request;
    }

    private static void logFailedUploads(BulkResponse response) {
        if (response.hasFailures()) {
            if (log.isTraceEnabled()) {
                Arrays.stream(response.getItems()).filter(BulkItemResponse::isFailed).forEach(item -> {
                    log.atTrace()
                        .withThrowable(ExceptionsHelper.unwrapCause(item.getFailure().getCause()))
                        .log("Metric failed to upload, discarding metrics. {}", item::getFailureMessage);
                });
            } else if (log.isDebugEnabled()) {
                Arrays.stream(response.getItems()).filter(BulkItemResponse::isFailed).findFirst().ifPresent(item -> {
                    log.atDebug()
                        .withThrowable(ExceptionsHelper.unwrapCause(item.getFailure().getCause()))
                        .log("Metric failed to upload, discarding metrics. {}", item::getFailureMessage);
                });
            }
        }
    }

    private interface BulkClient {
        void call(BulkRequest request, ActionListener<BulkResponse> listener);

        static BulkClient realClient(Client client) {
            return (request, listener) -> ClientHelper.executeAsyncWithOrigin(
                client,
                ClientHelper.INFERENCE_ORIGIN,
                TransportBulkAction.TYPE,
                request,
                listener
            );
        }
    }
}
