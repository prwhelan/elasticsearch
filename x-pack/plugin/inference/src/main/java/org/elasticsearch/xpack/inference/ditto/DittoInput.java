/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.ditto;

import org.elasticsearch.xpack.inference.common.Truncator;

import java.util.Map;

public interface DittoInput {
    Map<String, Object> headers();
    Map<String, Object> body();
    DittoTaskSettings taskSettings();

    DittoInput truncate(Truncator truncator);

    boolean[] truncationInfo();
}
