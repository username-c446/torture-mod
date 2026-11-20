package com.torturemod.api.port;

import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;

/** Точка расширения для приватов. Модули под конкретные моды добавляются позже. */
@FunctionalInterface
public interface RegionPolicy {
    RegionPolicy ALLOW_ALL = (actor, location, action) -> true;

    boolean allows(TargetId actor, Location location, Action action);

    enum Action {
        CAPTURE,
        TORTURE
    }
}
