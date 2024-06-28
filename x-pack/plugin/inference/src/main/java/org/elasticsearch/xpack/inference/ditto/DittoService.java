/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.ditto;

import org.elasticsearch.TransportVersion;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.inference.ChunkedInferenceServiceResults;
import org.elasticsearch.inference.ChunkingOptions;
import org.elasticsearch.inference.InferenceServiceResults;
import org.elasticsearch.inference.InputType;
import org.elasticsearch.inference.Model;
import org.elasticsearch.inference.ModelConfigurations;
import org.elasticsearch.inference.TaskType;
import org.elasticsearch.xpack.inference.common.Truncator;
import org.elasticsearch.xpack.inference.ditto.schema.DittoSchema;
import org.elasticsearch.xpack.inference.external.http.sender.DittoRequestManager;
import org.elasticsearch.xpack.inference.external.http.sender.DocumentsOnlyInput;
import org.elasticsearch.xpack.inference.external.http.sender.HttpRequestSender;
import org.elasticsearch.xpack.inference.services.SenderService;
import org.elasticsearch.xpack.inference.services.ServiceComponents;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.elasticsearch.xpack.inference.services.ServiceUtils.removeFromMapOrThrowIfNull;

public class DittoService extends SenderService {
    private final Map<TaskType, DittoSchema> dittoSchemas;
    private final String name;

    public DittoService(
        HttpRequestSender.Factory factory,
        ServiceComponents serviceComponents,
        Map<TaskType, DittoSchema> dittoSchemas,
        String name
    ) {
        super(factory, serviceComponents);
        this.dittoSchemas = dittoSchemas;
        this.name = name;
    }

    @Override
    protected void doInfer(
        Model model,
        List<String> input,
        Map<String, Object> taskSettings,
        InputType inputType,
        TimeValue timeout,
        ActionListener<InferenceServiceResults> listener
    ) {
        doInfer(model, null, input, taskSettings, inputType, timeout, listener);
    }

    @Override
    protected void doInfer(
        Model model,
        String query,
        List<String> input,
        Map<String, Object> taskSettings,
        InputType inputType,
        TimeValue timeout,
        ActionListener<InferenceServiceResults> listener
    ) {
        if (model instanceof DittoModel dittoModel) {
            var dittoSchema = dittoSchemas.get(dittoModel.getServiceSettings().taskType());

            // handle rate limiting
            var rateLimitingGroup = dittoModel.getServiceSettings().rateLimitGroup();
            var rateLimitingSettings = dittoModel.getServiceSettings().rateLimitSettings();

            // truncate input
            var tokenLimit = dittoModel.getServiceSettings().tokenLimit();
            var truncation = tokenLimit != null ? Truncator.truncate(input, tokenLimit) : null;
            var truncatedInput = truncation != null ? truncation.input() : input;

            // make http request & handle response
            var dittoInput = dittoSchema.parseInput(truncatedInput, truncation, query, taskSettings, inputType);
            var dittoRequest = new DittoRequest(dittoModel, getServiceComponents().truncator(), dittoInput);
            var responseHandler = dittoSchema.parseResponse();
            var dittoRequestManager = new DittoRequestManager(
                getServiceComponents().threadPool(),
                dittoModel.getInferenceEntityId(),
                rateLimitingGroup,
                rateLimitingSettings,
                dittoRequest,
                responseHandler
            );
            var dittoExecution = new DittoExecutableAction(getSender(), dittoRequestManager, dittoModel);
            dittoExecution.execute(new DocumentsOnlyInput(input), timeout, listener);
        }
    }

    @Override
    protected void doChunkedInfer(
        Model model,
        String query,
        List<String> input,
        Map<String, Object> taskSettings,
        InputType inputType,
        ChunkingOptions chunkingOptions,
        TimeValue timeout,
        ActionListener<List<ChunkedInferenceServiceResults>> listener
    ) {
        // the list<string> input that gets passed in the body gets limited by chunk size, so we make N calls where each N is a chunk
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void parseRequestConfig(
        String inferenceEntityId,
        TaskType taskType,
        Map<String, Object> config,
        Set<String> platformArchitectures,
        ActionListener<Model> parsedModelListener
    ) {
        ActionListener.completeWith(parsedModelListener, () -> {
            var dittoSchema = dittoSchemas.get(taskType);
            var serviceSettings = dittoSchema.parseServiceSettings(config);
            var taskSettings = dittoSchema.parseTaskSettings(config);
            var secretSettings = dittoSchema.parseSecretSettings(config);
            return new DittoModel(inferenceEntityId, taskType, dittoSchema.name(), serviceSettings, secretSettings, taskSettings);
        });
    }

    @Override
    public Model parsePersistedConfigWithSecrets(
        String inferenceEntityId,
        TaskType taskType,
        Map<String, Object> config,
        Map<String, Object> secrets
    ) {
        var serviceSettingsMap = removeFromMapOrThrowIfNull(config, ModelConfigurations.SERVICE_SETTINGS);
        var taskSettingsMap = removeFromMapOrThrowIfNull(config, ModelConfigurations.TASK_SETTINGS);
        var serviceSettings = DittoServiceSettings.fromStorage(serviceSettingsMap);
        var taskSettings = DittoTaskSettings.fromStorage(taskSettingsMap);
        var secretSettings = secrets != null ? DittoSecretSettings.fromStorage(secrets) : null;
        return new DittoModel(inferenceEntityId, taskType, name, serviceSettings, secretSettings, taskSettings);
    }

    @Override
    public Model parsePersistedConfig(String inferenceEntityId, TaskType taskType, Map<String, Object> config) {
        return parsePersistedConfigWithSecrets(inferenceEntityId, taskType, config, null);
    }

    @Override
    public TransportVersion getMinimalSupportedVersion() {
        return TransportVersion.current(); // TODO
    }
}
