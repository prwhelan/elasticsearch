/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.ditto;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.inference.InferenceServiceResults;
import org.elasticsearch.xpack.inference.external.action.ExecutableAction;
import org.elasticsearch.xpack.inference.external.http.sender.DittoRequestManager;
import org.elasticsearch.xpack.inference.external.http.sender.InferenceInputs;
import org.elasticsearch.xpack.inference.external.http.sender.Sender;

import static org.elasticsearch.xpack.inference.external.action.ActionUtils.constructFailedToSendRequestMessage;
import static org.elasticsearch.xpack.inference.external.action.ActionUtils.wrapFailuresInElasticsearchException;

public class DittoExecutableAction implements ExecutableAction {
    private final Sender sender;
    private final DittoRequestManager dittoRequestManager;
    private final DittoModel dittoModel;

    public DittoExecutableAction(Sender sender, DittoRequestManager dittoRequestManager, DittoModel dittoModel) {
        this.sender = sender;
        this.dittoRequestManager = dittoRequestManager;
        this.dittoModel = dittoModel;
    }

    @Override
    public void execute(InferenceInputs inferenceInputs, TimeValue timeout, ActionListener<InferenceServiceResults> listener) {
        ActionListener<InferenceServiceResults> wrappedListener = wrapFailuresInElasticsearchException(
            constructFailedToSendRequestMessage(dittoModel.getTaskSettings().uri(), dittoModel.humanReadableName()),
            listener
        );
        try {
            sender.send(dittoRequestManager, inferenceInputs, timeout, wrappedListener);
        } catch (Exception e) {
            wrappedListener.onFailure(e);
        }
    }
}
