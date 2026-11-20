package com.torturemod.api.torture;

import com.torturemod.api.model.TargetId;

import java.util.Optional;

@FunctionalInterface
public interface CaptiveMenuContributor {
    /** Кнопка для этого палача и пленного; пусто, если кнопка не нужна. */
    Optional<MenuButton> button(TargetId viewer, TargetId captive);
}
