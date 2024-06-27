/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.ditto;

import org.elasticsearch.TransportVersion;
import org.elasticsearch.index.mapper.vectors.DenseVectorFieldMapper;
import org.elasticsearch.inference.ServiceSettings;
import org.elasticsearch.inference.SimilarityMeasure;
import org.elasticsearch.xcontent.ToXContentObject;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xpack.inference.services.settings.RateLimitSettings;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.elasticsearch.xpack.inference.services.ServiceUtils.removeAsType;
import static org.elasticsearch.xpack.inference.services.ServiceUtils.removeStringOrThrowIfNull;

public class DittoServiceSettings extends DittoSettingsMap implements ServiceSettings {
    private final Integer tokenLimit;
    private final RateLimitSettings rateLimitSettings;
    private final int rateLimitGroup;

    public DittoServiceSettings(
        Map<String, Object> headers,
        Map<String, Object> body,
        XContentType contentType,
        Integer tokenLimit,
        RateLimitSettings rateLimitSettings,
        int rateLimitGroup
    ) {
        super(headers, body, contentType);
        this.tokenLimit = tokenLimit;
        this.rateLimitSettings = rateLimitSettings;
        this.rateLimitGroup = rateLimitGroup;
    }

    private DittoServiceSettings(
        Map<String, Object> storageMap,
        Integer tokenLimit,
        RateLimitSettings rateLimitSettings,
        int rateLimitGroup
    ) {
        super(storageMap);
        this.tokenLimit = tokenLimit;
        this.rateLimitSettings = rateLimitSettings;
        this.rateLimitGroup = rateLimitGroup;
    }

    @Override
    public String getWriteableName() {
        return "ditto_service_settings";
    }

    public int rateLimitGroup() {
        return rateLimitGroup;
    }

    public RateLimitSettings rateLimitSettings() {
        return rateLimitSettings;
    }

    @Override
    public TransportVersion getMinimalSupportedVersion() {
        return TransportVersion.current(); // TODO
    }

    @Override
    public ToXContentObject getFilteredXContentObject() {
        return this;
    }

    @Override
    public SimilarityMeasure similarity() {
        return ServiceSettings.super.similarity();
    }

    @Override
    public Integer dimensions() {
        return ServiceSettings.super.dimensions();
    }

    @Override
    public DenseVectorFieldMapper.ElementType elementType() {
        return ServiceSettings.super.elementType();
    }

    public Integer tokenLimit() {
        return tokenLimit;
    }

    protected XContentBuilder toXContentFragment(XContentBuilder builder, Params params) throws IOException {
        if (tokenLimit != null) {
            builder.field("tokenLimit", tokenLimit);
        }
        builder.field("RequestsPerMinute", rateLimitSettings.requestsPerTimeUnit());
        builder.field("rateLimitGroup", rateLimitGroup);
        return builder;
    }

    public static DittoServiceSettings fromStorage(Map<String, Object> storage) {
        var tokenLimit = removeAsType(storage, "tokenLimit", Integer.class);
        var rateLimitSettings = Optional.ofNullable(removeAsType(storage, "RequestsPerMinute", Long.class))
            .map(RateLimitSettings::new)
            .orElseThrow();
        var rateLimitGroup = Integer.parseInt(removeStringOrThrowIfNull(storage, "rateLimitGroup"));
        return new DittoServiceSettings(storage, tokenLimit, rateLimitSettings, rateLimitGroup);
    }
}
