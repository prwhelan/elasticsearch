/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.ditto;

import org.elasticsearch.xpack.inference.common.Truncator;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record ElasticDittoInput(
    Map<String, Object> headers,
    Map<String, Object> body,
    DittoTaskSettings taskSettings,
    String truncationKey,
    Truncator.TruncationResult truncation
) implements DittoInput {

    @Override
    @SuppressWarnings({ "unchecked" })
    public DittoInput truncate(Truncator truncator) {
        var truncation = truncator.truncate((List<String>) body().get(truncationKey));
        var truncatedBody = Stream.concat(
            body.entrySet().stream().filter(entry -> entry.getKey().equals(truncationKey) == false),
            Stream.of(Map.entry(truncationKey, truncation.input()))
        ).collect(Collectors.toMap(Map.Entry::getKey, entry -> (Object) entry.getValue()));
        return new ElasticDittoInput(headers, truncatedBody, taskSettings, truncationKey, truncation);
    }

    @Override
    public boolean[] truncationInfo() {
        return truncation != null ? truncation.truncated().clone() : null;
    }
}
