/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.telemetry.index;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.common.component.LifecycleListener;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.threadpool.Scheduler;
import org.elasticsearch.threadpool.ThreadPool;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class MlInferenceIndex {

    private static final Setting<Integer> METRIC_DELAY = Setting.intSetting(
        "xpack.inference.metric_delay_in_seconds",
        45,
        5,
        3600,
        Setting.Property.OperatorDynamic,
        Setting.Property.NodeScope
    );
    private static final Setting<Boolean> METRICS_ENABLED = Setting.boolSetting(
        "xpack.inference.metrics_enabled",
        false,
        Setting.Property.OperatorDynamic,
        Setting.Property.NodeScope
    );

    public static List<Setting<?>> getSettingsDefinitions() {
        return List.of(METRIC_DELAY, METRICS_ENABLED);
    }

    private final AtomicBoolean enabled;

    private MlInferenceIndex(boolean enabled) {
        this.enabled = new AtomicBoolean(enabled);
    }

    public static IndexBasedInferenceStats indexBasedInferenceStats(Plugin.PluginServices services) {
        var clusterService = services.clusterService();

        var observer = new MlInferenceIndex(METRICS_ENABLED.get(clusterService.getSettings()));

        var index = new InferenceIndexTemplateRegistry(
            services.environment().settings(),
            services.clusterService(),
            services.threadPool(),
            services.client(),
            services.xContentRegistry(),
            METRICS_ENABLED.get(clusterService.getSettings())
        );
        index.initialize();

        var indexBasedStats = new IndexBasedInferenceStats(observer.enabled::get);
        var statsPublisher = new IndexBasedInferenceStatsPublisher(
            indexBasedStats,
            services.client(),
            InferenceIndexTemplateRegistry.writeAlias()
        );

        var publisherThread = new PublisherThread(statsPublisher, services.threadPool(), METRIC_DELAY.get(clusterService.getSettings()));
        clusterService.addLifecycleListener(new LifecycleListener() {
            @Override
            public void afterStart() {
                if (observer.enabled.get()) {
                    publisherThread.start();
                }
            }

            @Override
            public void beforeStop() {
                publisherThread.stop();
            }
        });

        clusterService.getClusterSettings().addSettingsUpdateConsumer(METRIC_DELAY, delay -> publisherThread.delay = delay);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(METRICS_ENABLED, enabled -> {
            index.enabled(enabled);
            observer.enabled.set(enabled);
            if(enabled) {
                publisherThread.start();
            } else {
                publisherThread.stop();
            }
        });

        return indexBasedStats;
    }

    private static class PublisherThread {
        private static final Random random = new Random();

        private final IndexBasedInferenceStatsPublisher statsPublisher;
        private final ThreadPool threadPool;
        private final AtomicReference<Integer> currentRun;
        private Scheduler.ScheduledCancellable nextPublish;
        private volatile int delay;

        private PublisherThread(IndexBasedInferenceStatsPublisher statsPublisher, ThreadPool threadPool, int delay) {
            this.statsPublisher = statsPublisher;
            this.threadPool = threadPool;
            this.delay = delay;
            this.currentRun = new AtomicReference<>(null);
        }

        private void start() {
            if (currentRun.compareAndSet(null, random.nextInt())) {
                run(currentRun.get());
            }
        }

        // if someone calls stop and then start, we may have two threads running, that's fine for a bit, but the first thread shouldn't
        // keep going. so we're checking an integer to determine if we should continue. if the integer becomes null, we exit. if the
        // integer becomes a new value, we know a second thread has started, and the first thread exits.
        private void run(Integer ticket) {
            if (Objects.equals(currentRun.get(), ticket)) {
                nextPublish = threadPool.schedule(
                    () -> statsPublisher.run(ActionListener.running(() -> { run(ticket); })),
                    delayWithJitter(),
                    threadPool.generic()
                );
            }
        }

        private TimeValue delayWithJitter() {
            return TimeValue.timeValueSeconds(delay + random.nextInt(10));
        }

        private void stop() {
            currentRun.set(null);
            if (nextPublish != null) {
                nextPublish.cancel();
                nextPublish = null;
            }
            statsPublisher.run(ActionListener.noop()); // flush metrics
        }
    }
}
