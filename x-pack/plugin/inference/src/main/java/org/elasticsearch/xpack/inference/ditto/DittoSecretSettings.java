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

import java.util.Map;

public class DittoSecretSettings extends DittoSettingsMap implements SecretSettings {

    public DittoSecretSettings(Map<String, Object> secretSettings, XContentType contentType) {
        super(secretSettings, contentType);
    }

    @Override
    public String getWriteableName() {
        return "ditto_secret_settings";
    }

    @Override
    public TransportVersion getMinimalSupportedVersion() {
        return TransportVersion.current(); //TODO
    }

    public SecureString apiKey() {
        return null;
    }
}
