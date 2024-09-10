/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.external.http.sender;

import org.elasticsearch.common.Strings;

public abstract class InferenceInputs {
    private final boolean isStreaming;

    protected InferenceInputs(boolean isStreaming) {
        this.isStreaming = isStreaming;
    }

    public boolean isStreaming() {
        return isStreaming;
    }

    public static IllegalArgumentException createUnsupportedTypeException(InferenceInputs inferenceInputs) {
        return new IllegalArgumentException(Strings.format("Unsupported inference inputs type: [%s]", inferenceInputs.getClass()));
    }
}
