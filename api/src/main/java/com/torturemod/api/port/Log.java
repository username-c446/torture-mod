package com.torturemod.api.port;

public interface Log {
    void info(String message);

    void warn(String message);

    void error(String message, Throwable cause);
}
