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
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xpack.inference.services.ServiceUtils;
import org.elasticsearch.xpack.inference.services.settings.DefaultSecretSettings;

import java.io.IOException;
import java.util.Map;

import static org.elasticsearch.xpack.inference.services.ServiceUtils.removeAsType;
import static org.elasticsearch.xpack.inference.services.azureopenai.AzureOpenAiSecretSettings.API_KEY;

public class DittoSecretSettings extends DittoSettingsMap implements SecretSettings {
    private final DefaultSecretSettings defaultSecretSettings;

    public DittoSecretSettings(DefaultSecretSettings defaultSecretSettings, XContentType contentType) {
        super(Map.of(), Map.of(), contentType);
        this.defaultSecretSettings = defaultSecretSettings;
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
        return ServiceUtils.apiKey(defaultSecretSettings);
    }

    protected XContentBuilder toXContentFragment(XContentBuilder builder, Params params) throws IOException {
        return builder.field(API_KEY, apiKey().toString());
    }

    @SuppressWarnings({ "unchecked" })
    public static DittoSecretSettings fromStorage(Map<String, Object> storage) {
        var secretSettings = removeAsType(storage, "secret_settings", Map.class);
        return new DittoSecretSettings(DefaultSecretSettings.fromMap(secretSettings), XContentType.JSON);
    }
}
