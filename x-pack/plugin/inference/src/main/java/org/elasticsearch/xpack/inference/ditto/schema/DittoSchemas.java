/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.ditto.schema;

public class DittoSchemas {
    public static void main(String[] args) {
        System.out.println("start");
        var schema = new DittoSchemaLoader().load("cohere-embeddings.yml");
        System.out.println(schema);
        System.out.println("end");
    }

}
