package com.torturemod.forge;

import com.mojang.logging.LogUtils;
import com.torturemod.api.port.Scheduler;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class ForgeScheduler implements Scheduler {
    private final MinecraftServer server;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> pending = new ArrayList<>();
    /** Небольшой пул для ввода-вывода: потоки-демоны, чтобы не держать остановку сервера. */
    private final ExecutorService async = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "torturemod-async");
        thread.setDaemon(true);
        return thread;
    });
    private int statTicks;
    private long statTotalNanos;
    private long statMaxNanos;

    ForgeScheduler(MinecraftServer server) {
        this.server = server;
    }

    void tick() {
        long started = System.nanoTime();
        runTasks();
        long spent = System.nanoTime() - started;
        statTicks++;
        statTotalNanos += spent;
        statMaxNanos = Math.max(statMaxNanos, spent);
    }

    private void runTasks() {
        // Задачи могут планировать новые во время обхода, поэтому они попадают в список на следующем тике.
        entries.addAll(pending);
        pending.clear();
        Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.cancelled || entry.fire()) {
                iterator.remove();
            }
        }
    }

    @Override
    public Task everyTick(Runnable action) {
        return add(new Entry(action, 0, true));
    }

    @Override
    public Task later(int ticks, Runnable action) {
        return add(new Entry(action, ticks, false));
    }

    @Override
    public void runOnMainThread(Runnable action) {
        server.execute(action);
    }

    @Override
    public void runAsync(Runnable work) {
        async.execute(() -> {
            try {
                work.run();
            } catch (RuntimeException | Error e) {
                LogUtils.getLogger().error("Torture Mod background task failed", e);
            }
        });
    }

    @Override
    public TickStats takeStats() {
        TickStats stats = new TickStats(statTicks, statTotalNanos, statMaxNanos);
        statTicks = 0;
        statTotalNanos = 0;
        statMaxNanos = 0;
        return stats;
    }

    /** Ждёт начатые фоновые задачи при остановке сервера. */
    void shutdown() {
        async.shutdown();
        try {
            async.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Task add(Entry entry) {
        pending.add(entry);
        return entry;
    }

    private static final class Entry implements Task {
        private final Runnable action;
        private final boolean repeating;
        private int delay;
        private boolean cancelled;

        Entry(Runnable action, int delay, boolean repeating) {
            this.action = action;
            this.delay = delay;
            this.repeating = repeating;
        }

        /** @return true, если задача завершена */
        boolean fire() {
            if (delay > 0) {
                delay--;
                return false;
            }
            action.run();
            return !repeating;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
