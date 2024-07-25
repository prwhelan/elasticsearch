/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.telemetry.index;

import org.elasticsearch.client.internal.Client;
import org.elasticsearch.cluster.metadata.ComposableIndexTemplate;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.xcontent.NamedXContentRegistry;
import org.elasticsearch.xpack.core.ClientHelper;
import org.elasticsearch.xpack.core.ilm.LifecyclePolicy;
import org.elasticsearch.xpack.core.template.IndexTemplateConfig;
import org.elasticsearch.xpack.core.template.IndexTemplateRegistry;
import org.elasticsearch.xpack.core.template.LifecyclePolicyConfig;

import java.util.List;
import java.util.Map;

public class InferenceIndexTemplateRegistry extends IndexTemplateRegistry {

    private static final String ROOT_RESOURCE_PATH = "/inference/";
    private static final String VERSION_ID_PATTERN = "xpack.inference.version";
    private static final int VERSION_ID = 1;

    private static final Map.Entry<String, String> VERSION = Map.entry(VERSION_ID_PATTERN, String.valueOf(VERSION_ID));
    private static final Map.Entry<String, String> LIFECYCLE = Map.entry("xpack.inference.policy.name", ".inference-stats-policy");
    private static final Map.Entry<String, String> ALIAS = Map.entry("xpack.inference.policy.rollover_alias", "inference-stats");

    private static final LifecyclePolicyConfig lifecyclePolicyConfig = lifecycle();
    private static final LifecyclePolicy lifecyclePolicy = lifecyclePolicyConfig.load(LifecyclePolicyConfig.DEFAULT_X_CONTENT_REGISTRY);
    private final Map<String, ComposableIndexTemplate> composableIndexTemplateConfigs = parseComposableTemplates(stats());

    private volatile boolean enabled;

    public InferenceIndexTemplateRegistry(
        Settings nodeSettings,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        NamedXContentRegistry xContentRegistry,
        boolean enabled
    ) {
        super(nodeSettings, clusterService, threadPool, client, xContentRegistry);
        this.enabled = enabled;
    }

    void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    private static LifecyclePolicyConfig lifecycle() {
        return new LifecyclePolicyConfig(LIFECYCLE.getValue(), ROOT_RESOURCE_PATH + "inference_stats_policy.json", Map.ofEntries(VERSION));
    }

    private static IndexTemplateConfig stats() {
        return new IndexTemplateConfig(
            "inference.stats.template",
            ROOT_RESOURCE_PATH + "inference_stats_index_template.json",
            VERSION_ID,
            VERSION_ID_PATTERN,
            Map.ofEntries(VERSION, LIFECYCLE)
        );
    }

    @Override
    protected String getOrigin() {
        return ClientHelper.INFERENCE_ORIGIN;
    }

    @Override
    protected boolean requiresMasterNode() {
        return true;
    }

    @Override
    protected List<LifecyclePolicy> getLifecyclePolicies() {
        return enabled ? List.of(lifecyclePolicy) : List.of();
    }

    @Override
    protected Map<String, ComposableIndexTemplate> getComposableTemplateConfigs() {
        return enabled ? composableIndexTemplateConfigs : Map.of();
    }

    @Override
    protected List<LifecyclePolicyConfig> getLifecycleConfigs() {
        return enabled ? List.of(lifecyclePolicyConfig) : List.of();
    }

    public static String writeAlias() {
        return ALIAS.getValue();
    }
}
