/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.ditto.schema;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.inference.InferenceResults;
import org.elasticsearch.inference.InferenceServiceResults;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.XContentParserConfiguration;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xpack.inference.external.http.HttpResult;
import org.elasticsearch.xpack.inference.external.http.retry.ResponseHandler;
import org.elasticsearch.xpack.inference.external.http.retry.RetryException;
import org.elasticsearch.xpack.inference.external.request.Request;
import org.elasticsearch.xpack.inference.logging.ThrottlerManager;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VoyageResponseHandler implements ResponseHandler {
    @Override
    public void validateResponse(ThrottlerManager throttlerManager, Logger logger, Request request, HttpResult result)
        throws RetryException {

    }

    @Override
    @SuppressWarnings({ "unchecked" })
    public InferenceServiceResults parseResult(Request request, HttpResult response) throws RetryException {
        var parserConfig = XContentParserConfiguration.EMPTY.withDeprecationHandler(LoggingDeprecationHandler.INSTANCE);
        try (XContentParser jsonParser = XContentFactory.xContent(XContentType.JSON).createParser(parserConfig, response.body())) {
            var elasticMap = new LinkedHashMap<String, Object>();
            var voyageMap = jsonParser.map();
            var textEmbedding = (List<Object>) elasticMap.computeIfAbsent("text_embedding", k -> new LinkedList<>());
            var dataList = (List<Map<String, Object>>) voyageMap.get("data");
            dataList.forEach(data -> {
                var voyageEmbeddings = (List<Double>) data.get("embedding");
                textEmbedding.add(Map.of("embedding", voyageEmbeddings.stream().toList()));
            });
            return new Results(elasticMap);
        } catch (Exception e) {
            throw new ElasticsearchParseException("Failed to parse response", e);
        }
    }

    @Override
    public String getRequestType() {
        return "";
    }

    static class Results implements InferenceServiceResults {
        private final Map<String, Object> map;

        private Results(Map<String, Object> map) {
            this.map = map;
        }

        Results(StreamInput in) {
            var parserConfig = XContentParserConfiguration.EMPTY.withDeprecationHandler(LoggingDeprecationHandler.INSTANCE);
            try (XContentParser jsonParser = XContentFactory.xContent(XContentType.JSON).createParser(parserConfig, in)) {
                this.map = jsonParser.map();
            } catch (Exception e) {
                throw new ElasticsearchParseException("Failed to parse response", e);
            }
        }

        @Override
        public List<? extends InferenceResults> transformToCoordinationFormat() {
            return List.of();
        }

        @Override
        public List<? extends InferenceResults> transformToLegacyFormat() {
            return List.of();
        }

        @Override
        public Map<String, Object> asMap() {
            return map;
        }

        @Override
        public String getWriteableName() {
            return "";
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            try (XContentBuilder builder = XContentBuilder.builder(XContentType.JSON, Set.of(), Set.of())) {
                builder.startObject();
                out.writeString(Strings.toString(toXContent(builder, EMPTY_PARAMS)));
                builder.endObject();
            }
            XContentHelper.writeTo(out, XContentType.JSON);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.mapContents(map);
            return builder;
        }
    }
}
