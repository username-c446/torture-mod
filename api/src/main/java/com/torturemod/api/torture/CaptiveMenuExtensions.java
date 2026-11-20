package com.torturemod.api.torture;

/** Точка расширения меню пленного: фичи добавляют свои кнопки, не зная о его устройстве. */
public interface CaptiveMenuExtensions {
    void add(CaptiveMenuContributor contributor);
}
