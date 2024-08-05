/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.external.http.retry;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.protocol.AbstractAsyncResponseConsumer;
import org.apache.http.nio.util.SimpleInputBuffer;
import org.apache.http.protocol.HttpContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.xpack.inference.external.http.HttpResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class StreamingResponseHandler extends AbstractAsyncResponseConsumer<HttpResult> {
    private static final Logger logger = LogManager.getLogger(StreamingResponseHandler.class);
    private volatile HttpResponse response;
    private volatile SimpleInputBuffer buf;
    private final ActionListener<HttpResult> listener;
    private volatile ContentType contentType;

    public StreamingResponseHandler(ActionListener<HttpResult> listener) {
        this.listener = listener;
    }

    @Override
    protected void onResponseReceived(HttpResponse httpResponse) throws HttpException, IOException {
        logger.error("onResponseReceived");
        this.response = httpResponse;
    }

    @Override
    protected void onEntityEnclosed(HttpEntity httpEntity, ContentType contentType) throws IOException {
        logger.error("httpEntity");
        /*
        long len = httpEntity.getContentLength();
        if (len < 0) {
            len = 4096;
        }
        this.buf = new SimpleInputBuffer((int) len, HeapByteBufferAllocator.INSTANCE);*/
        this.response.setEntity(httpEntity);
    }

    @Override
    protected void onContentReceived(ContentDecoder contentDecoder, IOControl ioControl) throws IOException {
        logger.error("onContentReceived");
        var buffer = new SimpleInputBuffer(4096);
        var consumed = buffer.consumeContent(contentDecoder);
        var allBytes = new byte[consumed];
        buffer.read(allBytes);
        // we can have empty bytes, don't bother sending them
        if (allBytes.length > 0) {
            listener.onResponse(new HttpResult(response, allBytes));
        }
    }

    @Override
    protected HttpResult buildResult(HttpContext httpContext) throws Exception {
        logger.error("buildResult");
        return new HttpResult(response, "[DONE]".getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void releaseResources() {
        logger.error("releaseResources");
        this.response = null;
    }

    @Override
    protected void onClose() throws IOException {
        logger.error("onClose");
        super.onClose();
    }

    @Override
    protected ContentType getContentType(HttpEntity entity) {
        logger.error("getContentType {} {}", entity.getContentType().getName(), entity.getContentType().getValue());
        contentType = super.getContentType(entity);
        return contentType;
    }

    @Override
    public Exception getException() {
        logger.error("getException");
        return super.getException();
    }

    @Override
    public HttpResult getResult() {
        logger.error("getResult");
        return super.getResult();
    }

    @Override
    public boolean isDone() {
        return super.isDone();
    }
}
