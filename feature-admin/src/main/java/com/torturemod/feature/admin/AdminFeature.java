package com.torturemod.feature.admin;

import com.torturemod.api.feature.Feature;
import com.torturemod.api.feature.FeatureContext;

/** Команды администратора, журнал, иммунитет. */
public final class AdminFeature implements Feature {
    @Override
    public String id() {
        return "admin";
    }

    @Override
    public void start(FeatureContext context) {
    }
}
