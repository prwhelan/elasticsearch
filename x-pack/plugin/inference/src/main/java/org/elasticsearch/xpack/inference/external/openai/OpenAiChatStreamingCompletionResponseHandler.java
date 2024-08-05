/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.external.openai;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.inference.InferenceServiceResults;
import org.elasticsearch.xcontent.XContentFactory;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.XContentParserConfiguration;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xpack.core.inference.results.StreamingChatCompletionResults;
import org.elasticsearch.xpack.inference.external.http.HttpResult;
import org.elasticsearch.xpack.inference.external.http.retry.ResponseParser;
import org.elasticsearch.xpack.inference.external.request.Request;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.elasticsearch.xpack.inference.external.response.XContentUtils.moveToFirstToken;
import static org.elasticsearch.xpack.inference.external.response.XContentUtils.positionParserAtTokenAfterField;

public class OpenAiChatStreamingCompletionResponseHandler extends OpenAiResponseHandler {
    private static final Logger logger = LogManager.getLogger(OpenAiChatStreamingCompletionResponseHandler.class);
    private static final StreamingChatCompletionResults.Result END_OF_STREAM_RESULT = new StreamingChatCompletionResults.Result("");
    private static final StreamingChatCompletionResults END_OF_STREAM = new StreamingChatCompletionResults(
        true,
        List.of(END_OF_STREAM_RESULT)
    );
    private static final String FAILED_TO_FIND_FIELD_TEMPLATE = "Failed to find required field [%s] in OpenAI chat completions response";

    public OpenAiChatStreamingCompletionResponseHandler(String requestType) {
        super(requestType, new Parser());
    }

    // :(
    public static class Parser implements ResponseParser {

        // we can sometimes get json structures broken up, so we need to stitch those together, this i guess is to spec?
        // i sure hope this is called sequentially
        private volatile String previousTokens = "";
        private AtomicReference<StreamingChatCompletionResults> results = new AtomicReference<>();

        @Override
        public InferenceServiceResults apply(Request request, HttpResult result) throws IOException {
            if (result.isBodyEmpty()) {
                return END_OF_STREAM;
            }

            var parserConfig = XContentParserConfiguration.EMPTY.withDeprecationHandler(LoggingDeprecationHandler.INSTANCE);

            // we should make a parser for the mime type text/event-stream so we don't have to do this
            var body = previousTokens + new String(result.body());
            var lines = body.split("\\n\\n", -1); // we actually want trailing empty strings, because stitching

            boolean isEndOfStream = false;
            var collector = new ArrayList<StreamingChatCompletionResults.Result>();
            for (var i = 0; i < lines.length - 1; i++) {
                var line = lines[i];
                // idk what to do with other lines yet
                if (line.contains("data:")) {
                    line = line.replace("data:", "").trim(); // turn it into JSON
                    if ("[done]".equalsIgnoreCase(line)) {
                        logger.error("this should be at the end? why isn't it? because [done] is followed by two new lines?");
                        isEndOfStream = true;
                        collector.add(END_OF_STREAM_RESULT);
                    } else {
                        collector.add(new StreamingChatCompletionResults.Result(parse(parserConfig, line)));
                    }
                }
            }

            previousTokens = lines[lines.length - 1];

            if (previousTokens.isBlank() == false) {
                logger.error("what am i {}", previousTokens);
            }

            // this should always be the last line
            if ("[done]".equalsIgnoreCase(previousTokens.trim())) {
                logger.error("this should happen? maybe? idk");
                isEndOfStream = true;
                collector.add(END_OF_STREAM_RESULT);
            }

            if (results.get() == null) {
                results.set(new StreamingChatCompletionResults(isEndOfStream, collector));
            } else {
                results.get().publish(isEndOfStream, collector);
            }
            // whatever, return the results, the listener is already ignoring subsequent calls
            // this whole class should probably be removed
            return results.get();
        }

        private static String parse(XContentParserConfiguration parserConfig, String json) throws RuntimeException {
            // idk how to parse this right now, just ignore it
            if (json.contains("\"finish_reason\":\"stop\"")) {
                return "";
            }

            try (XContentParser jsonParser = XContentFactory.xContent(XContentType.JSON).createParser(parserConfig, json)) {
                moveToFirstToken(jsonParser);

                XContentParser.Token token = jsonParser.currentToken();
                ensureExpectedToken(XContentParser.Token.START_OBJECT, token, jsonParser);

                positionParserAtTokenAfterField(jsonParser, "choices", FAILED_TO_FIND_FIELD_TEMPLATE);

                jsonParser.nextToken();
                ensureExpectedToken(XContentParser.Token.START_OBJECT, jsonParser.currentToken(), jsonParser);

                positionParserAtTokenAfterField(jsonParser, "delta", FAILED_TO_FIND_FIELD_TEMPLATE);

                token = jsonParser.currentToken();

                ensureExpectedToken(XContentParser.Token.START_OBJECT, token, jsonParser);

                positionParserAtTokenAfterField(jsonParser, "content", FAILED_TO_FIND_FIELD_TEMPLATE);

                XContentParser.Token contentToken = jsonParser.currentToken();
                ensureExpectedToken(XContentParser.Token.VALUE_STRING, contentToken, jsonParser);
                return jsonParser.text();
            } catch (Exception e) {
                throw new RuntimeException("Failing json: " + json, e);
            }
        }
    }
}
