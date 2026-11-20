package com.torturemod.api.prison;

import com.torturemod.api.model.Area;
import com.torturemod.api.model.TargetId;

import java.util.Optional;

/**
 * Права из мода или плагина приватов. Например, мэр города получает роль владельца тюрьмы,
 * стоящей на земле города. Сопоставление ролей задаётся в конфиге.
 */
public interface OwnershipSource {
    /** Роль игрока в привате на этой земле, например mayor; пусто, если её нет. */
    Optional<String> claimRole(TargetId player, Area area);

    /** Может ли игрок регистрировать тюрьму здесь; пусто, если приват не решает. */
    Optional<Boolean> mayRegister(TargetId player, Area area);
}
