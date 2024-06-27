/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.ditto.schema;

import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.inference.InputType;
import org.elasticsearch.inference.TaskType;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xpack.inference.common.Truncator;
import org.elasticsearch.xpack.inference.ditto.DittoInput;
import org.elasticsearch.xpack.inference.ditto.DittoSecretSettings;
import org.elasticsearch.xpack.inference.ditto.DittoServiceSettings;
import org.elasticsearch.xpack.inference.ditto.DittoTaskSettings;
import org.elasticsearch.xpack.inference.ditto.ElasticDittoInput;
import org.elasticsearch.xpack.inference.external.http.retry.ResponseHandler;
import org.elasticsearch.xpack.inference.services.settings.RateLimitSettings;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DittoSchema {
    private final String name;
    private final String type;
    private final URI uri;
    private final Integer tokenLimit;
    private final Long rateLimit;
    private final Object rateLimitGroup;
    private final Map<List<String>, DittoSchemaMapping> taskSettingsHeaders;
    private final Map<List<String>, DittoSchemaMapping> taskSettingsBody;
    private final Map<List<String>, DittoSchemaMapping> serviceSettingsHeaders;
    private final Map<List<String>, DittoSchemaMapping> serviceSettingsBody;
    private final Map<List<String>, DittoSchemaMapping> secretSettingsHeaders;
    private final Map<List<String>, DittoSchemaMapping> inferenceRequestHeaders;
    private final Map<List<String>, DittoSchemaMapping> inferenceRequestBody;
    private final Map<String, String> hardcodedHeaders;
    private final Map<String, String> hardcodedBody;
    private final Map<List<String>, DittoSchemaMapping> inferenceResponseBody;

    DittoSchema(
        String name,
        String type,
        URI uri,
        Integer tokenLimit,
        Long rateLimit,
        Object rateLimitGroup,
        Map<List<String>, DittoSchemaMapping> taskSettingsHeaders,
        Map<List<String>, DittoSchemaMapping> taskSettingsBody,
        Map<List<String>, DittoSchemaMapping> serviceSettingsHeaders,
        Map<List<String>, DittoSchemaMapping> serviceSettingsBody,
        Map<List<String>, DittoSchemaMapping> secretSettingsHeaders,
        Map<List<String>, DittoSchemaMapping> inferenceRequestHeaders,
        Map<List<String>, DittoSchemaMapping> inferenceRequestBody,
        Map<String, String> hardcodedHeaders,
        Map<String, String> hardcodedBody,
        Map<List<String>, DittoSchemaMapping> inferenceResponseBody
    ) {
        this.name = name;
        this.type = type;
        this.uri = uri;
        this.tokenLimit = tokenLimit;
        this.rateLimit = rateLimit;
        this.rateLimitGroup = rateLimitGroup;
        this.taskSettingsHeaders = taskSettingsHeaders;
        this.taskSettingsBody = taskSettingsBody;
        this.serviceSettingsHeaders = serviceSettingsHeaders;
        this.serviceSettingsBody = serviceSettingsBody;
        this.secretSettingsHeaders = secretSettingsHeaders;
        this.inferenceRequestHeaders = inferenceRequestHeaders;
        this.inferenceRequestBody = inferenceRequestBody;
        this.hardcodedHeaders = hardcodedHeaders;
        this.hardcodedBody = hardcodedBody;
        this.inferenceResponseBody = inferenceResponseBody;
    }

    public DittoServiceSettings parseServiceSettings(Map<String, Object> config) {
        return new DittoServiceSettings(
            parse(serviceSettingsHeaders, config),
            parse(serviceSettingsBody, config),
            XContentType.JSON,
            tokenLimit,
            new RateLimitSettings(rateLimit),
            rateLimitGroup == null ? UUID.randomUUID().hashCode() : rateLimitGroup.hashCode(),
            taskType()
        );
    }

    @SuppressWarnings({ "unchecked" })
    private static Map<String, Object> parse(Map<List<String>, DittoSchemaMapping> settings, Map<String, Object> config) {
        var output = new HashMap<String, Object>();
        settings.forEach((keys, locationSettings) -> {
            var keyIter = keys.iterator();
            Object value = null;
            var map = config;
            while (value == null && keyIter.hasNext()) {
                value = map.get(keyIter.next());
                if (value == null) {
                    if (locationSettings.required()) {
                        throw new ElasticsearchParseException("Service Settings is missing [" + String.join(",", keys) + "]");
                    } else if (locationSettings.defaultValue() != null) {
                        output.put(locationSettings.key(), locationSettings.defaultValue());
                    }
                    break;
                }
                if (keyIter.hasNext()) {
                    if (value instanceof Map<?, ?>) {
                        map = (Map<String, Object>) value;
                    } else {
                        throw new ElasticsearchParseException("Service Settings nested map to [" + String.join(",", keys) + "]");
                    }
                }
            }
            if (value != null) {
                output.put(locationSettings.key(), value);
            } else {
                if (locationSettings.required()) {
                    throw new ElasticsearchParseException("Service Settings is missing [" + String.join(",", keys) + "]");
                } else if (locationSettings.defaultValue() != null) {
                    output.put(locationSettings.key(), locationSettings.defaultValue());
                }
            }
        });

        return output;
    }

    public DittoTaskSettings parseTaskSettings(Map<String, Object> config) {
        var mappedHeaders = parse(taskSettingsHeaders, config);
        var mappedBody = parse(taskSettingsBody, config);
        var headers = Stream.concat(mappedHeaders.entrySet().stream(), hardcodedHeaders.entrySet().stream())
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> (Object) entry.getValue()));
        var body = Stream.concat(mappedBody.entrySet().stream(), hardcodedBody.entrySet().stream())
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> (Object) entry.getValue()));
        return new DittoTaskSettings(uri, headers, body, XContentType.JSON);
    }

    public DittoSecretSettings parseSecretSettings(Map<String, Object> config) {
        return new DittoSecretSettings(parse(secretSettingsHeaders, config), XContentType.JSON);
    }

    public DittoInput parseInput(
        List<String> input,
        Truncator.TruncationResult truncation,
        String query,
        Map<String, Object> taskSettings,
        InputType inputType
    ) {
        var headers = parseInput(input, query, inputType, inferenceRequestHeaders);
        var body = parseInput(input, query, inputType, inferenceRequestBody);
        var overrideTaskSettings = parseTaskSettings(taskSettings);
        var truncationKey = inferenceRequestBody.entrySet().stream().filter(entry -> {
            var list = entry.getKey();
            return list.size() > 1 && list.get(1).equals("input");
        }).map(Map.Entry::getValue).map(DittoSchemaMapping::key).findFirst().orElseThrow();
        return new ElasticDittoInput(headers, body, overrideTaskSettings, truncationKey, truncation);
    }

    private Map<String, Object> parseInput(
        List<String> input,
        String query,
        InputType inputType,
        Map<List<String>, DittoSchemaMapping> mappings
    ) {
        var map = new HashMap<String, Object>();
        mappings.forEach((location, mapping) -> {
            if (location.size() > 1 && location.get(1).equals("input")) {
                map.put(mapping.key(), input);
            } else if (location.size() > 1 && location.get(1).equals("query")) {
                if (query == null && mapping.required()) {
                    throw new ElasticsearchParseException("No query found, but one is required.");
                } else if (query == null && mapping.defaultValue() != null) {
                    map.put(mapping.key(), mapping.defaultValue().toString());
                } else {
                    map.put(mapping.key(), query);
                }
            } else if (location.size() > 1 && location.get(1).equals("input_type")) {
                map.put(mapping.key(), inputType.toString());
            }
        });
        return map;
    }

    public ResponseHandler parseResponse() {
        return new DittoResponseHandler(map -> parse(inferenceResponseBody, map));
    }

    public String name() {
        return name;
    }

    public TaskType taskType() {
        return type != null ? TaskType.fromString(type) : TaskType.ANY;
    }
}
