/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.ditto.schema;

import org.elasticsearch.inference.InferenceServiceExtension;
import org.elasticsearch.inference.TaskType;
import org.elasticsearch.xpack.inference.ditto.DittoService;
import org.elasticsearch.xpack.inference.external.http.sender.HttpRequestSender;
import org.elasticsearch.xpack.inference.services.ServiceComponents;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DittoSchemas {
    private static final List<String> schemaFiles = List.of();

    public static Stream<InferenceServiceExtension.Factory> dittoServices(
        HttpRequestSender.Factory factory,
        ServiceComponents serviceComponents
    ) {
        return groupedSchemas().entrySet()
            .stream()
            .map(entry -> new DittoService(factory, serviceComponents, entry.getValue(), entry.getKey()))
            .map(dittoService -> context -> dittoService);
    }

    private static Map<String, Map<TaskType, DittoSchema>> groupedSchemas() {
        var schemaLoader = new DittoSchemaLoader();
        return schemaFiles.stream()
            .map(schemaLoader::load)
            .collect(Collectors.groupingBy(DittoSchema::name, Collectors.toMap(DittoSchema::taskType, Function.identity())));
    }

}
