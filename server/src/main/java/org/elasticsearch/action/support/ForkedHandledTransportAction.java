/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.action.support;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.transport.Transports;

import java.util.concurrent.Executor;
import java.util.function.Function;

import static org.elasticsearch.common.util.concurrent.EsExecutors.DIRECT_EXECUTOR_SERVICE;

public abstract class ForkedHandledTransportAction<Request extends ActionRequest, Response extends ActionResponse> extends
    HandledTransportAction<Request, Response> {
    private final Executor executor;
    private final Function<Runnable, Runnable> preserveContext;

    protected ForkedHandledTransportAction(
        String actionName,
        TransportService transportService,
        ActionFilters actionFilters,
        Writeable.Reader<Request> requestReader,
        Executor executor
    ) {
        super(actionName, transportService, actionFilters, requestReader, DIRECT_EXECUTOR_SERVICE);
        this.executor = executor;
        this.preserveContext = transportService.getThreadPool().getThreadContext()::preserveContext;
    }

    @Override
    protected void doExecute(Task task, Request request, ActionListener<Response> listener) {
        try {
            executor.execute(preserveContext.apply(() -> {
                assert Transports.assertNotTransportThread("operation work must always fork to an appropriate executor");
                operation(task, request, listener);
            }));
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    protected abstract void operation(Task task, Request request, ActionListener<Response> listener);
}
