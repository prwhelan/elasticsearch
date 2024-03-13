/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.transform.transforms;

import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.xpack.transform.transforms.scheduling.TransformScheduler;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.elasticsearch.core.Strings.format;

class TransformRetryableListener<Response> implements TransformScheduler.Listener {
    private final AtomicReference<Instant> firstOccurrence = new AtomicReference<>();
    private final AtomicReference<String> errorMessage = new AtomicReference<>();
    private final AtomicInteger retryCount = new AtomicInteger(0);
    private final Consumer<ActionListener<Response>> action;
    private final ActionListener<Response> listener;
    private final ActionListener<Boolean> retryScheduledListener;
    private final Supplier<Boolean> shouldRetry;

    TransformRetryableListener(
        Consumer<ActionListener<Response>> action,
        ActionListener<Response> listener,
        ActionListener<Boolean> retryScheduledListener,
        Supplier<Boolean> shouldRetry
    ) {
        this.action = action;
        this.listener = listener;
        this.retryScheduledListener = retryScheduledListener;
        this.shouldRetry = shouldRetry;
    }

    @Override
    public void triggered(TransformScheduler.Event event) {
        action.accept(ActionListener.wrap(this::actionSucceeded, this::actionFailed));
    }

    private void actionSucceeded(Response r) {
        if(retryCount.get() == 0) {
            retryScheduledListener.onResponse(false);
        }
        listener.onResponse(r);
    }

    private void actionFailed(Exception e) {
        setFirstFailureOccurrence();
        recordFirstError(e);
        if(shouldRetry.get()) {
            retryCount.updateAndGet(i -> {
                if(i == 0) {
                    retryScheduledListener.onResponse(true);
                    return 1;
                }
                return i + 1;
            });
        } else {
            listener.onFailure(e);
        }
    }

    private void setFirstFailureOccurrence() {
        firstOccurrence.updateAndGet(f -> {
            if(f == null) {
                return Instant.now();
            } else {
                return f;
            }
        });
    }

    private void recordFirstError(Exception e) {
        errorMessage.updateAndGet(m -> {
            if(m == null) {
                return format("Retrying Transform action due to error: %s.", ExceptionsHelper.unwrapCause(e));
            } else {
                return m;
            }
        });
    }
}
