/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.ditto;

import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xpack.inference.common.Truncator;
import org.elasticsearch.xpack.inference.external.request.HttpRequest;
import org.elasticsearch.xpack.inference.external.request.Request;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.elasticsearch.xpack.inference.external.request.RequestUtils.createAuthBearerHeader;

public class DittoRequest implements Request {
    private final DittoModel dittoModel;
    private final Truncator truncator;
    private final DittoInput dittoInput;

    public DittoRequest(DittoModel dittoModel, Truncator truncator, DittoInput dittoInput) {
        this.dittoModel = dittoModel;
        this.truncator = truncator;
        this.dittoInput = dittoInput;
    }

    @Override
    public HttpRequest createHttpRequest() {
        var httpPost = new HttpPost(getURI());

        httpPost.setHeader(HttpHeaders.ACCEPT, XContentType.JSON.mediaType());
        httpPost.setHeader(HttpHeaders.CONTENT_TYPE, XContentType.JSON.mediaType());
        httpPost.setHeader(createAuthBearerHeader(dittoModel.getSecretSettings().apiKey()));
        // TODO brittle, we should guarantee Map<String, String> during storage
        dittoModel.getServiceSettings().headers().forEach((key, value) -> httpPost.setHeader(key, String.valueOf(value)));
        override(dittoModel.getTaskSettings().headers(), dittoInput.taskSettings().headers()).forEach(
            (key, value) -> httpPost.setHeader(key, String.valueOf(value))
        );
        dittoInput.headers().forEach((key, value) -> httpPost.setHeader(key, String.valueOf(value)));

        ToXContent bodyContent = (builder, params) -> {
            var safeField = safeField(builder);
            dittoModel.getServiceSettings().body().forEach(safeField);
            dittoInput.taskSettings().body().forEach(safeField);
            override(dittoModel.getTaskSettings().body(), dittoInput.taskSettings().body()).forEach(safeField);
            dittoInput.body().forEach(safeField);
            return builder;
        };

        var byteEntity = new ByteArrayEntity(Strings.toString(bodyContent).getBytes(StandardCharsets.UTF_8));
        httpPost.setEntity(byteEntity);

        return new HttpRequest(httpPost, getInferenceEntityId());
    }

    private Map<String, Object> override(Map<String, Object> base, Map<String, Object> override) {
        var map = new HashMap<String, Object>();
        map.putAll(base);
        map.putAll(override);
        return map;
    }

    private BiConsumer<String, Object> safeField(XContentBuilder builder) {
        return (key, value) -> {
            try {
                builder.field(key, value);
            } catch (IOException e) {
                throw new ElasticsearchParseException("Failed to parse key [" + key + "] and value [" + value + "].", e);
            }
        };
    }

    @Override
    public URI getURI() {
        return dittoModel.getTaskSettings().uri();
    }

    @Override
    public DittoRequest truncate() {
        return new DittoRequest(dittoModel, truncator, dittoInput.truncate(truncator));
    }

    @Override
    public boolean[] getTruncationInfo() {
        return dittoInput.truncationInfo();
    }

    @Override
    public String getInferenceEntityId() {
        return dittoModel.getInferenceEntityId();
    }
}
