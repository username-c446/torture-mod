package com.torturemod.api.prison;

import com.torturemod.api.model.TargetId;
import com.torturemod.api.torture.MenuButton;

import java.util.Optional;

/** Раздел пульта тюрьмы от другой фичи, например армия мобов. */
@FunctionalInterface
public interface ConsoleSection {
    /** Кнопка раздела для этого игрока; пусто, если раздел ему не нужен. */
    Optional<MenuButton> button(String prisonId, TargetId viewer);
}
