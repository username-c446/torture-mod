package com.torturemod.api.prison;

import com.torturemod.api.model.Area;
import com.torturemod.api.model.TargetId;

public record PrisonInfo(String id, String name, TargetId owner, Area area) {
}
