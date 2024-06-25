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
import org.elasticsearch.xcontent.XContentType;

import java.net.URI;
import java.util.Map;

public class DittoServiceSettings extends DittoSettingsMap implements ServiceSettings {

    public DittoServiceSettings(Map<String, Object> serviceSettings, XContentType contentType) {
        super(serviceSettings, contentType);
    }

    @Override
    public String getWriteableName() {
        return "ditto_service_settings";
    }

    @Override
    public TransportVersion getMinimalSupportedVersion() {
        return TransportVersion.current(); //TODO
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

    @Override
    public boolean isFragment() {
        return ServiceSettings.super.isFragment();
    }

    public URI uri() {
        return null;
    }
}
