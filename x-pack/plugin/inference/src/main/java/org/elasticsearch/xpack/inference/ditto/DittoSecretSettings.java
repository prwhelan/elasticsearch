/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.ditto;

import org.elasticsearch.TransportVersion;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.inference.SecretSettings;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xpack.inference.services.ServiceUtils;
import org.elasticsearch.xpack.inference.services.settings.DefaultSecretSettings;

import java.util.Map;

public class DittoSecretSettings extends DittoSettingsMap implements SecretSettings {

    public DittoSecretSettings(Map<String, Object> secretSettings, XContentType contentType) {
        super(secretSettings, Map.of(), contentType);
    }

    @Override
    public String getWriteableName() {
        return "ditto_secret_settings";
    }

    @Override
    public TransportVersion getMinimalSupportedVersion() {
        return TransportVersion.current(); // TODO
    }

    @Override
    public Map<String, Object> headers() {
        return Map.of();
    }

    @Override
    public Map<String, Object> body() {
        return Map.of();
    }

    public SecureString apiKey() {
        return ServiceUtils.apiKey(DefaultSecretSettings.fromMap(headers()));
    }
}
