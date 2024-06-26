/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.ditto;

import org.elasticsearch.TransportVersion;
import org.elasticsearch.inference.TaskSettings;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentType;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Optional;

import static org.elasticsearch.xpack.inference.services.ServiceUtils.removeAsType;
import static org.elasticsearch.xpack.inference.services.ServiceUtils.removeFromMapOrThrowIfNull;

public class DittoTaskSettings extends DittoSettingsMap implements TaskSettings {
    private final URI uri;

    public DittoTaskSettings(URI uri, Map<String, Object> headers, Map<String, Object> body, XContentType contentType) {
        super(headers, body, contentType);
        this.uri = uri;
    }

    private DittoTaskSettings(Map<String, Object> storageMap, URI uri) {
        super(storageMap);
        this.uri = uri;
    }

    @Override
    public String getWriteableName() {
        return "ditto_task_settings";
    }

    @Override
    public TransportVersion getMinimalSupportedVersion() {
        return TransportVersion.current(); // TODO
    }

    public URI uri() {
        return uri;
    }

    @Override
    protected XContentBuilder toXContentFragment(XContentBuilder builder, Params params) throws IOException {
        return builder.field("uri", uri.toString());
    }

    public static DittoTaskSettings fromStorage(Map<String, Object> storageMap) {
        var uri = Optional.ofNullable(removeAsType(storageMap, "uri", String.class))
            .map(URI::create)
            .orElseThrow();
        return new DittoTaskSettings(storageMap, uri);
    }
}
