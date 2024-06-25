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
    private final Map<String, Object> settings;
    private final XContentType contentType;

    DittoSettingsMap(Map<String, Object> settings, XContentType contentType) {
        this.settings = settings;
        this.contentType = contentType;
    }

    protected Map<String, Object> settings() {
        return settings;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        try (
            XContentBuilder builder = XContentBuilder.builder(contentType, Set.of(), Set.of()).map(settings);
        ) {
            out.writeString(Strings.toString(builder));
        }
        XContentHelper.writeTo(out, contentType);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field("taskSettings");
        builder.map(settings);
        builder.field("contentType", contentType);
        builder.endObject();
        return builder;
    }
}
