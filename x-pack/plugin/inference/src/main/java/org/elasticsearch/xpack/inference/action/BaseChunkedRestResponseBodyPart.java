/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.action;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.common.bytes.ReleasableBytesReference;
import org.elasticsearch.common.io.stream.BytesStream;
import org.elasticsearch.common.io.stream.RecyclerBytesStreamOutput;
import org.elasticsearch.common.recycler.Recycler;
import org.elasticsearch.common.xcontent.ChunkedToXContent;
import org.elasticsearch.core.IOUtils;
import org.elasticsearch.core.Releasables;
import org.elasticsearch.core.RestApiVersion;
import org.elasticsearch.core.Streams;
import org.elasticsearch.rest.ChunkedRestResponseBodyPart;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.function.Consumer;

/**
 * Mostly copied from {@link ChunkedRestResponseBodyPart#fromXContent(ChunkedToXContent, ToXContent.Params, RestChannel)}, except that one
 * doesn't allow for Continuations.  This one will just farm out the continuations to a consumer.
 */
public class BaseChunkedRestResponseBodyPart implements ChunkedRestResponseBodyPart {
    private static final Logger logger = LogManager.getLogger(BaseChunkedRestResponseBodyPart.class);

    private final ToXContent.Params params;

    private final OutputStream out = new OutputStream() {
        @Override
        public void write(int b) throws IOException {
            target.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            target.write(b, off, len);
        }
    };

    private final XContentBuilder builder;
    private final Iterator<? extends ToXContent> serialization;
    private final boolean isLastPart;
    private final Consumer<ActionListener<ChunkedRestResponseBodyPart>> listenerConsumer;

    private BytesStream target;

    public BaseChunkedRestResponseBodyPart(
        ChunkedToXContent chunkedToXContent,
        ToXContent.Params params,
        RestChannel channel,
        boolean isLastPart,
        Consumer<ActionListener<ChunkedRestResponseBodyPart>> listenerConsumer
    ) throws IOException {
        this.params = params;

        this.builder = channel.newBuilder(channel.request().getXContentType(), null, true, Streams.noCloseStream(out));
        this.serialization = builder.getRestApiVersion() == RestApiVersion.V_7
            ? chunkedToXContent.toXContentChunkedV7(params)
            : chunkedToXContent.toXContentChunked(params);
        this.isLastPart = isLastPart;
        this.listenerConsumer = listenerConsumer;
    }

    @Override
    public boolean isPartComplete() {
        return serialization.hasNext() == false;
    }

    @Override
    public boolean isLastPart() {
        return isLastPart;
    }

    @Override
    public void getNextPart(ActionListener<ChunkedRestResponseBodyPart> listener) {
        listenerConsumer.accept(listener);
    }

    @Override
    public ReleasableBytesReference encodeChunk(int sizeHint, Recycler<BytesRef> recycler) throws IOException {
        try {
            final RecyclerBytesStreamOutput chunkStream = new RecyclerBytesStreamOutput(recycler);
            assert target == null;
            target = chunkStream;
            while (serialization.hasNext()) {
                serialization.next().toXContent(builder, params);
                if (chunkStream.size() >= sizeHint) {
                    break;
                }
            }
            if (serialization.hasNext() == false) {
                builder.close();
            }
            final var result = new ReleasableBytesReference(chunkStream.bytes(), () -> Releasables.closeExpectNoException(chunkStream));
            target = null;
            return result;
        } catch (Exception e) {
            logger.error("failure encoding chunk", e);
            throw e;
        } finally {
            if (target != null) {
                // assert false : "failure encoding chunk";
                IOUtils.closeWhileHandlingException(target);
                target = null;
            }
        }
    }

    @Override
    public String getResponseContentTypeString() {
        return builder.getResponseContentTypeString();
    }
}
