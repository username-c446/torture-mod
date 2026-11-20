package com.torturemod.api.port;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Задачи мода. Всё, что трогает мир или состояние фич, идёт на основном потоке сервера.
 * Ввод-вывод и тяжёлые вычисления над готовыми данными уходят в фон, а результат возвращается
 * на основной поток. Фоновая задача не должна ждать основной поток: так дедлок невозможен.
 */
public interface Scheduler {
    Task everyTick(Runnable action);

    Task later(int ticks, Runnable action);

    /** Переводит работу на основной поток: например, по завершении запроса к хранилищу. */
    void runOnMainThread(Runnable action);

    /** Фоновая задача. Не трогает мир и состояние фич. */
    void runAsync(Runnable work);

    /** Считает в фоне и передаёт результат или ошибку на основной поток. */
    default <T> void supplyAsync(Supplier<T> work, Consumer<T> onMainThread, Consumer<Throwable> onError) {
        runAsync(() -> {
            T result;
            try {
                result = work.get();
            } catch (RuntimeException | Error e) {
                runOnMainThread(() -> onError.accept(e));
                return;
            }
            runOnMainThread(() -> onMainThread.accept(result));
        });
    }

    /** Сколько времени заняли задачи мода на тиках с прошлого запроса; запрос обнуляет счёт. */
    TickStats takeStats();

    interface Task {
        void cancel();
    }

    record TickStats(int ticks, long totalNanos, long maxNanos) {
    }
}
