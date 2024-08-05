/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.external.openai;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.elasticsearch.inference.InferenceResults;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xpack.inference.external.http.HttpResult;
import org.elasticsearch.xpack.inference.external.request.RequestTests;

import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OpenAiChatStreamingCompletionResponseHandlerTests extends ESTestCase {
    @SuppressWarnings("checkstyle:LineLength")
    private static final String response =
        """
            data: {"id":"chatcmpl-9sODzy9L9VUS3584DlsXdiObDaGh1","object":"chat.completion.chunk","created":1722262615,"model":"gpt-4o-mini-2024-07-18","system_fingerprint":"fp_9b0abffe81","choices":[{"index":0,"delta":{"role":"assistant","content":""},"logprobs":null,"finish_reason":null}]}

                    data: {"id":"chatcmpl-9sODzy9L9VUS3584DlsXdiObDaGh1","object":"chat.completion.chunk","created":1722262615,"model":"gpt-4o-mini-2024-07-18","system_fingerprint":"fp_9b0abffe81","choices":[{"index":0,"delta":{"content":"\\""},"logprobs":null,"finish_reason":null}]}

                    data: {"id":"chatcmpl-9sODzy9L9VUS3584DlsXdiObDaGh1","object":"chat.completion.chunk","created":1722262615,"model":"gpt-4o-mini-2024-07-18","system_fingerprint":"fp_9b0abffe81","choices":[{"index":0,"delta":{"content":"Elastic"},"logprobs":null,"finish_reason":null}]}

                    data: {"id":"chatcmpl-9sODzy9L9VUS3584DlsXdiObDaGh1","object":"chat.completion.chunk","created":1722262615,"model":"gpt-4o-mini-2024-07-18","system_fingerprint":"fp_9b0abffe81","choices":[{"index":0,"delta":{"content":"\\""},"logprobs":null,"finish_reason":null}]}

                    data: {"id":"chatcmpl-9sODzy9L9VUS3584DlsXdiObDaGh1","object":"chat.completion.chunk","created":1722262615,"model":"gpt-4o-mini-2024-07-18","system_fingerprint":"fp_9b0abffe81","choices":[{"index":0,"delta":{"content":" can"},"logprobs":null,"finish_reason":null}]}

                    data: {"id":"chatcmpl-9sODzy9L9VUS3584DlsXdiObDaGh1","object":"chat.completion.chunk","created":1722262615,"model":"gpt-4o-mini-2024-07-18","system_fingerprint":"fp_9b0abffe81","choices":[{"index":0,"delta":{"content":" refer"},"logprobs":null,"finish_reason":null}]}

            """;

    public void test() {
        var statusLine = mock(StatusLine.class);

        var httpResponse = mock(HttpResponse.class);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        var header = mock(Header.class);
        when(header.getElements()).thenReturn(new HeaderElement[] {});
        when(httpResponse.getFirstHeader(anyString())).thenReturn(header);

        var mockRequest = RequestTests.mockRequest("id");
        var httpResult = new HttpResult(httpResponse, new byte[] {});
        var handler = new OpenAiChatStreamingCompletionResponseHandler("");

        var inferenceServiceResults = handler.parseResult(
            mockRequest,
            new HttpResult(httpResponse, response.getBytes(StandardCharsets.UTF_8))
        );

        var str = inferenceServiceResults.transformToLegacyFormat()
            .stream()
            .map(InferenceResults::predictedValue)
            .map(Object::toString)
            .collect(Collectors.joining());
        assertThat(str, equalTo("\"Elastic\" can refer"));
    }
}
