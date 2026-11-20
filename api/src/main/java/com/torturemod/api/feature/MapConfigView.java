package com.torturemod.api.feature;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Конфиг поверх вложенных Map: так выглядит разобранный YAML. */
public final class MapConfigView implements ConfigView {
    private static final Object MISSING = new Object();

    private final Map<String, ?> root;

    public MapConfigView(Map<String, ?> root) {
        this.root = root;
    }

    /** Собирает вложенную структуру из плоских путей: удобно в тестах. */
    @SuppressWarnings("unchecked")
    public static MapConfigView fromFlat(Map<String, ?> flat) {
        Map<String, Object> root = new LinkedHashMap<>();
        flat.forEach((path, value) -> {
            String[] parts = path.split("\\.");
            Map<String, Object> current = root;
            for (int i = 0; i < parts.length - 1; i++) {
                current = (Map<String, Object>) current.computeIfAbsent(parts[i], key -> new LinkedHashMap<>());
            }
            current.put(parts[parts.length - 1], value);
        });
        return new MapConfigView(root);
    }

    @Override
    public boolean has(String path) {
        return find(path) != MISSING;
    }

    @Override
    public String getString(String path, String fallback) {
        Object value = find(path);
        return value == MISSING || value == null ? fallback : value.toString();
    }

    @Override
    public int getInt(String path, int fallback) {
        return find(path) instanceof Number number ? number.intValue() : fallback;
    }

    @Override
    public double getDouble(String path, double fallback) {
        return find(path) instanceof Number number ? number.doubleValue() : fallback;
    }

    @Override
    public boolean getBoolean(String path, boolean fallback) {
        return find(path) instanceof Boolean flag ? flag : fallback;
    }

    @Override
    public List<String> getStringList(String path) {
        return find(path) instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ConfigView> getSections(String path) {
        if (!(find(path) instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (ConfigView) new MapConfigView((Map<String, ?>) item))
                .toList();
    }

    private Object find(String path) {
        Object current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(part)) {
                return MISSING;
            }
            current = map.get(part);
        }
        return current;
    }
}
