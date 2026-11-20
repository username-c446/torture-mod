package com.torturemod.api.items;

/** Определяет типы пыток предмета: правила конфига, затем автоопределение. */
public interface ItemClassifier {
    Classification classify(String itemId);
}
