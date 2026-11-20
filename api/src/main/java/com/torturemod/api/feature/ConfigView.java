package com.torturemod.api.feature;

import java.util.List;

/** Неизменяемый снимок конфига. Пути разделяются точкой: capture.items. Ключи могут содержать двоеточие. */
public interface ConfigView {
    boolean has(String path);

    String getString(String path, String fallback);

    int getInt(String path, int fallback);

    double getDouble(String path, double fallback);

    boolean getBoolean(String path, boolean fallback);

    List<String> getStringList(String path);

    /** Список вложенных разделов, например правил. */
    List<ConfigView> getSections(String path);
}
