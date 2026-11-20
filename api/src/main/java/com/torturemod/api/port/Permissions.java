package com.torturemod.api.port;

import com.torturemod.api.model.TargetId;

public interface Permissions {
    String USE = "torturemod.use";
    String ADMIN = "torturemod.admin";
    String IMMUNE = "torturemod.immune";

    boolean has(TargetId player, String node);
}
