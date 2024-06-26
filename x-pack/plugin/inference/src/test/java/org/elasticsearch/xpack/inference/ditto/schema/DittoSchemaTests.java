/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.ditto.schema;

import org.elasticsearch.test.ESTestCase;
import org.hamcrest.Matchers;

public class DittoSchemaTests extends ESTestCase {
    public void testCohereEmbeddings() {

        var schema = new DittoSchemaLoader().load("/org/elasticsearch/xpack/inference/ditto/schema/cohere-embeddings.yml");
        assertThat(schema, Matchers.notNullValue());
        assertThat(schema.name(), Matchers.equalTo("cohere"));
    }
}
