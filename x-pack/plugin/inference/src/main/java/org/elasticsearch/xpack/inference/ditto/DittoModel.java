/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.ditto;

import org.elasticsearch.inference.Model;
import org.elasticsearch.inference.ModelConfigurations;
import org.elasticsearch.inference.ModelSecrets;
import org.elasticsearch.inference.TaskType;

public class DittoModel extends Model {
    private final DittoServiceSettings serviceSettings;
    private final DittoSecretSettings secretSettings;
    private final DittoTaskSettings taskSettings;

    public DittoModel(
        String inferenceEntityId,
        TaskType taskType,
        String service,
        DittoServiceSettings serviceSettings,
        DittoSecretSettings secretSettings,
        DittoTaskSettings taskSettings
    ) {
        super(
            new ModelConfigurations(inferenceEntityId, taskType, service, serviceSettings, taskSettings),
            new ModelSecrets(secretSettings)
        );
        this.serviceSettings = serviceSettings;
        this.secretSettings = secretSettings;
        this.taskSettings = taskSettings;
    }

    @Override
    public DittoServiceSettings getServiceSettings() {
        return serviceSettings;
    }

    @Override
    public DittoSecretSettings getSecretSettings() {
        return secretSettings;
    }

    @Override
    public DittoTaskSettings getTaskSettings() {
        return taskSettings;
    }
}
