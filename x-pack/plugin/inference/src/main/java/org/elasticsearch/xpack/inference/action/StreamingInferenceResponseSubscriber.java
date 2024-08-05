/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.action;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.rest.ChunkedRestResponseBodyPart;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.action.RestChunkedToXContentListener;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xpack.core.inference.action.InferenceAction;

import java.io.IOException;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Starts a REST response via the channel, and then uses the Flow API to continuously pipe into the channel.
 *
 * I *think* we need to maintain an interim queue? I can't tell if the Apache HTTP Response Consumer will only consume one thread at a time,
 * but in case it doesn't we're currently throwing things in a thread-safe FIFO queue.
 */
public class StreamingInferenceResponseSubscriber extends RestChunkedToXContentListener<InferenceAction.Response>
    implements
        Flow.Subscriber<InferenceAction.Response> {
    private final AtomicBoolean isResponding = new AtomicBoolean(false);
    private final AtomicReference<ActionListener<ChunkedRestResponseBodyPart>> continueSendingData = new AtomicReference<>();
    private final BlockingDeque<InferenceAction.Response> resultQueue = new LinkedBlockingDeque<>();

    public StreamingInferenceResponseSubscriber(RestChannel channel) {
        super(channel);
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {

    }

    @Override
    public void onNext(InferenceAction.Response response) {
        try {
            if (isResponding.compareAndSet(false, true)) {
                if (continueSendingData.get() != null) {
                    continueSendingData.get().onResponse(nextPart(response));
                } else {
                    channel.sendResponse(
                        RestResponse.chunked(getRestStatus(response), nextPart(response), releasableFromResponse(response))
                    );
                }
            } else {
                if (resultQueue.offer(response) == false) {
                    // handle a full queue?
                }
            }
        } catch (IOException e) {
            onError(e);
        }
    }

    @Override
    protected void processResponse(InferenceAction.Response response) throws IOException {
        var inferenceServiceResults = response.getResults();
        if (inferenceServiceResults.isStreaming()) {
            response.subscribe(this);
            onNext(response);
        } else {
            super.processResponse(response);
        }
    }

    private ChunkedRestResponseBodyPart nextPart(InferenceAction.Response response) throws IOException {
        return new BaseChunkedRestResponseBodyPart(
            response,
            ToXContent.EMPTY_PARAMS,
            channel,
            response.getResults().isFinishedStreaming(),
            nextPartListener(response.getResults().isFinishedStreaming())
        );
    }

    private Consumer<ActionListener<ChunkedRestResponseBodyPart>> nextPartListener(boolean isEndOfStream) {
        if (isEndOfStream) {
            return listener -> {
                assert false : "no continuations";
                listener.onFailure(new IllegalStateException("no continuations available"));
            };
        } else {
            return listener -> {
                var next = resultQueue.poll();
                if (next != null) {
                    ActionListener.completeWith(listener, () -> nextPart(next));
                } else {
                    continueSendingData.set(listener);
                    isResponding.set(false);
                }
            };
        }
    }

    @Override
    public void onError(Throwable throwable) {
        onFailure(new ElasticsearchException(throwable));
    }

    @Override
    public void onComplete() {}
}
