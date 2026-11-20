package com.torturemod.forge;

import com.mojang.logging.LogUtils;
import com.torturemod.api.port.Log;
import org.slf4j.Logger;

final class ForgeLog implements Log {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void info(String message) {
        LOGGER.info(message);
    }

    @Override
    public void warn(String message) {
        LOGGER.warn(message);
    }

    @Override
    public void error(String message, Throwable cause) {
        LOGGER.error(message, cause);
    }
}
