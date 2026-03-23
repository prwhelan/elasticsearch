/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.transform.rest;

import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.rest.yaml.ClientYamlTestExecutionContext;
import org.elasticsearch.test.rest.yaml.section.DoSection;
import org.elasticsearch.test.rest.yaml.section.ExecutableSection;
import org.elasticsearch.xcontent.XContentLocation;
import org.elasticsearch.xcontent.XContentType;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Wraps a {@code transform.start_transform} {@link DoSection} so that after the start call succeeds,
 * we poll {@code GET _transform/{id}/_stats} until the transform state is no longer {@code "stopped"}.
 * <p>
 * This is necessary because the start API can take some number of seconds after the persistent task
 * is assigned, but the transform may not have fully initialized yet. YAML REST tests have no built-in
 * retry mechanism, so without this wrapper, assertions on transform state immediately after
 * start can fail intermittently.
 * <p>
 * Polling uses a separate admin {@link RestClient} so as not to overwrite the test execution
 * context's last response (which subsequent YAML assertions read from).
 */
final class DoStartTransformAndWait implements ExecutableSection {

    private final DoSection delegate;
    private final Supplier<RestClient> adminClientSupplier;

    DoStartTransformAndWait(DoSection delegate, Supplier<RestClient> adminClientSupplier) {
        assert "transform.start_transform".equals(delegate.getApiCallSection().getApi());
        this.delegate = delegate;
        this.adminClientSupplier = adminClientSupplier;
    }

    @Override
    public XContentLocation getLocation() {
        return delegate.getLocation();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void execute(ClientYamlTestExecutionContext executionContext) throws IOException {
        delegate.execute(executionContext);

        if (delegate.getCatch() != null) {
            return;
        }

        String transformId = delegate.getApiCallSection().getParams().get("transform_id");
        if (transformId == null) {
            return;
        }

        try {
            ESTestCase.assertBusy(() -> {
                try {
                    Request statsRequest = new Request("GET", "/_transform/" + transformId + "/_stats");
                    Response statsResponse = adminClientSupplier.get().performRequest(statsRequest);
                    Map<String, Object> body = XContentHelper.convertToMap(
                        XContentType.JSON.xContent(),
                        statsResponse.getEntity().getContent(),
                        false
                    );
                    List<Map<String, Object>> transforms = (List<Map<String, Object>>) body.get("transforms");
                    Map<String, Object> transformStats = transforms.get(0);
                    if (hasBeenInitialized(transformStats)) {
                        return;
                    }
                    throw new AssertionError(
                        "Transform [" + transformId + "] has not initialized yet (state=[stopped], no evidence of execution)"
                    );
                } catch (IOException e) {
                    throw new AssertionError("Failed to get transform stats for [" + transformId + "]", e);
                }
            }, 30, TimeUnit.SECONDS);
        } catch (Exception | AssertionError e) {
            // The transform may be a batch transform on an empty index that completed before
            // our first poll, leaving stats indistinguishable from an unstarted transform.
            // Proceed and let subsequent test assertions determine correctness.
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean hasBeenInitialized(Map<String, Object> transformStats) {
        String state = (String) transformStats.get("state");
        if ("stopped".equals(state) == false) {
            return true;
        }
        Map<String, Object> stats = (Map<String, Object>) transformStats.get("stats");
        if (stats != null) {
            if (numberGreaterThanZero(stats.get("trigger_count")) || numberGreaterThanZero(stats.get("pages_processed"))) {
                return true;
            }
        }
        Map<String, Object> checkpointing = (Map<String, Object>) transformStats.get("checkpointing");
        if (checkpointing != null) {
            Map<String, Object> last = (Map<String, Object>) checkpointing.get("last");
            if (last != null && numberGreaterThanZero(last.get("checkpoint"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean numberGreaterThanZero(Object value) {
        return value instanceof Number n && n.longValue() > 0;
    }
}
