package com.torturemod.feature.prison;

import com.torturemod.api.prison.PrisonRight;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Роль в команде тюрьмы. Пресеты одинаковы во всех тюрьмах и не меняются, свои роли создаёт владелец. */
final class Role {
    static final String OWNER = "owner";
    static final String COOWNER = "coowner";
    static final String WARDEN = "warden";

    static final Map<String, Role> PRESETS = presets();

    final String id;
    String name;
    final Set<PrisonRight> rights;
    final boolean preset;

    Role(String id, String name, Set<PrisonRight> rights, boolean preset) {
        this.id = id;
        this.name = name;
        this.rights = rights.isEmpty() ? EnumSet.noneOf(PrisonRight.class) : EnumSet.copyOf(rights);
        this.preset = preset;
    }

    private static Map<String, Role> presets() {
        Map<String, Role> roles = new LinkedHashMap<>();
        roles.put(OWNER, new Role(OWNER, "", EnumSet.allOf(PrisonRight.class), true));
        roles.put(COOWNER, new Role(COOWNER, "", EnumSet.complementOf(EnumSet.of(PrisonRight.DELETE)), true));
        roles.put(WARDEN, new Role(WARDEN, "", EnumSet.of(PrisonRight.CAPTURE, PrisonRight.TORTURE, PrisonRight.CONVOY,
                PrisonRight.CUFF, PrisonRight.INTERROGATE, PrisonRight.DEALS), true));
        return Map.copyOf(roles);
    }
}
