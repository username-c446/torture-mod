package com.torturemod.api.feature;

public interface Messages {
    /** Текст по ключу; заполнители {0}, {1} заменяются аргументами. */
    String text(String key, Object... args);
}
