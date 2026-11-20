package com.torturemod.api.captivity;

import com.torturemod.api.model.Location;

/** Место содержания поверх клеток: например, тюрьма со своими границами и правилом побега. */
public interface Containment {
    /** Пленный под этой охраной: правила клетки к нему не применяются, освобождение решает она. */
    boolean claims(Captive captive);

    /** Это место удерживает пленного: туда можно отпустить его с поводка. */
    boolean holds(Captive captive, Location location);
}
