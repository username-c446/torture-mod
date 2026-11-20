package com.torturemod.api.port;

import com.torturemod.api.model.TargetId;

import java.util.Optional;

/**
 * Поводок и транспорт. Видимую верёвку и посадку обеспечивает платформа: клиенту без мода
 * игра не даёт привязать игрока, поэтому верёвка идёт к невидимой опоре рядом с пленным.
 */
public interface Convoy {
    void showLeash(TargetId captive, TargetId holder);

    void hideLeash(TargetId captive);

    /** Задаёт скорость цели: так поводок тянет пленного за палачом. */
    void push(TargetId target, double vx, double vy, double vz);

    /** Лодка, вагонетка или ездовое животное. */
    boolean isVehicle(TargetId entity);

    /** @return false, если в транспорте нет места */
    boolean mount(TargetId rider, TargetId vehicle);

    void dismount(TargetId rider);

    Optional<TargetId> vehicleOf(TargetId rider);
}
