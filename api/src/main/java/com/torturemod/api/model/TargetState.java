package com.torturemod.api.model;

/**
 * Снимок состояния цели, загруженной в мир.
 *
 * @param typeId тип сущности, например minecraft:zombie
 */
public record TargetState(TargetId id, TargetKind kind, String typeId, String name, Location location,
                          float health, float maxHealth) {
}
