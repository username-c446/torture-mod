package com.torturemod.kernel;

import com.torturemod.api.feature.ServiceRegistry;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

final class KernelServices implements ServiceRegistry {
    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    @Override
    public <T> void provide(Class<T> type, T service) {
        if (services.putIfAbsent(type, service) != null) {
            throw new IllegalStateException("Service already provided: " + type.getName());
        }
    }

    @Override
    public <T> Optional<T> find(Class<T> type) {
        return Optional.ofNullable(type.cast(services.get(type)));
    }
}
