/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.external.http.sender;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.inference.InferenceServiceResults;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.xpack.inference.ditto.DittoRequest;
import org.elasticsearch.xpack.inference.external.http.retry.RequestSender;
import org.elasticsearch.xpack.inference.external.http.retry.ResponseHandler;
import org.elasticsearch.xpack.inference.services.settings.RateLimitSettings;

import java.util.List;
import java.util.function.Supplier;

public class DittoRequestManager extends BaseRequestManager {
    private static final Logger logger = LogManager.getLogger(DittoRequestManager.class);
    private final DittoRequest dittoRequest;
    private final ResponseHandler responseHandler;

    public DittoRequestManager(
        ThreadPool threadPool,
        String inferenceEntityId,
        Object rateLimitGroup,
        RateLimitSettings rateLimitSettings,
        DittoRequest dittoRequest,
        ResponseHandler responseHandler
    ) {
        super(threadPool, inferenceEntityId, rateLimitGroup, rateLimitSettings);
        this.dittoRequest = dittoRequest;
        this.responseHandler = responseHandler;
    }

    @Override
    public void execute(
        String query,
        List<String> input,
        RequestSender requestSender,
        Supplier<Boolean> hasRequestCompletedFunction,
        ActionListener<InferenceServiceResults> listener
    ) {
        execute(
            new ExecutableInferenceRequest(requestSender, logger, dittoRequest, responseHandler, hasRequestCompletedFunction, listener)
        );
    }
}
