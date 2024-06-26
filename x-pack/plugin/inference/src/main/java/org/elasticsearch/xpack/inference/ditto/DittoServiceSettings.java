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

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Optional;

import static org.elasticsearch.xpack.inference.services.ServiceUtils.removeAsType;

public class DittoServiceSettings extends DittoSettingsMap implements ServiceSettings {
    private final Integer tokenLimit;

    public DittoServiceSettings(Map<String, Object> headers, Map<String, Object> body, XContentType contentType, Integer tokenLimit) {
        super(headers, body, contentType);
        this.tokenLimit = tokenLimit;
    }

    private DittoServiceSettings(Map<String, Object> storageMap, Integer tokenLimit) {
        super(storageMap);
        this.tokenLimit = tokenLimit;
    }

    @Override
    public String getWriteableName() {
        return "ditto_service_settings";
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
        return builder;
    }

    public static DittoServiceSettings fromStorage(Map<String, Object> storage) {
        var tokenLimit = removeAsType(storage, "tokenLimit", Integer.class);
        return new DittoServiceSettings(storage, tokenLimit);
    }
}
