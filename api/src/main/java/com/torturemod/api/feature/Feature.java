package com.torturemod.api.feature;

/** Подключаемая часть мода. Фичи общаются только через {@link EventBus} и порты. */
public interface Feature {
    String id();

    void start(FeatureContext context);

    default void stop() {
    }
}
