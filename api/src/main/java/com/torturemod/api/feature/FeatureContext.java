package com.torturemod.api.feature;

import com.torturemod.api.Platform;
import com.torturemod.api.port.Storage;

import java.time.Clock;

public interface FeatureContext {
    Platform platform();

    Storage storage();

    EventBus bus();

    /** Текущий снимок конфига. Не кэшируйте его: после перезагрузки вернётся новый. */
    ConfigView config();

    Messages messages();

    /** Подкоманды общей команды мода /torturemod. */
    CommandRegistry commands();

    /** Сервисы, которые фичи предоставляют друг другу через интерфейсы из api. */
    ServiceRegistry services();

    Clock clock();
}
