/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.ditto;

import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.VersionedNamedWriteable;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.xcontent.ToXContentObject;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentType;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

abstract class DittoSettingsMap implements ToXContentObject, VersionedNamedWriteable {
    private final Map<String, Object> headers;
    private final Map<String, Object> body;
    private final XContentType contentType;

    DittoSettingsMap(Map<String, Object> headers, Map<String, Object> body, XContentType contentType) {
        this.headers = headers;
        this.body = body;
        this.contentType = contentType;
    }

    @SuppressWarnings({ "unchecked" })
    DittoSettingsMap(Map<String, Object> storageMap) {
        this.headers = (Map<String, Object>) storageMap.get("headers");
        this.body = (Map<String, Object>) storageMap.get("body");
        this.contentType = XContentType.valueOf((String) storageMap.get("contentType"));
    }

    public Map<String, Object> headers() {
        return headers;
    }

    public Map<String, Object> body() {
        return body;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        try (XContentBuilder builder = XContentBuilder.builder(contentType, Set.of(), Set.of())) {
            out.writeString(Strings.toString(toXContent(builder, EMPTY_PARAMS)));
        }
        XContentHelper.writeTo(out, contentType);
    }

    @Override
    public final XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field("headers").map(headers);
        builder.field("body").map(body);
        builder.field("contentType", contentType);
        return toXContentFragment(builder, params).endObject();
    }

    protected XContentBuilder toXContentFragment(XContentBuilder builder, Params params) throws IOException {
        return builder;
    }
}
