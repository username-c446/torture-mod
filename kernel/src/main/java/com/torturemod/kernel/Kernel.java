package com.torturemod.kernel;

import com.torturemod.api.Platform;
import com.torturemod.api.feature.CommandRegistry;
import com.torturemod.api.feature.ConfigReloaded;
import com.torturemod.api.feature.ConfigView;
import com.torturemod.api.feature.EventBus;
import com.torturemod.api.feature.Feature;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.feature.Messages;
import com.torturemod.api.feature.ServiceRegistry;
import com.torturemod.api.port.Scheduler;
import com.torturemod.api.port.Storage;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Жизненный цикл мода: конфиг, хранилище, фичи. Не зависит от платформы. */
public final class Kernel implements FeatureContext {
    private static final String COMMAND_ROOT = "torturemod";

    private final Platform platform;
    private final Storage storage;
    private final List<Feature> features;
    private final SimpleEventBus bus;
    private final KernelCommands commands;
    private final KernelServices services = new KernelServices();
    private final Clock clock = Clock.systemUTC();
    private final Deque<Feature> started = new ArrayDeque<>();

    // Заменяются целиком при перезагрузке.
    private volatile ConfigView config;
    private volatile Messages messages;
    /** Время обработки событий игры с прошлого замера; меняется только на основном потоке. */
    private long eventNanos;

    public Kernel(Platform platform, Storage storage, List<Feature> features) {
        this.platform = platform;
        this.storage = storage;
        this.features = List.copyOf(features);
        this.bus = new SimpleEventBus(platform.log());
        this.commands = new KernelCommands(platform, () -> messages);
    }

    public void start() {
        loadConfig();
        platform.log().info("Storage ready: " + storage.open().join());
        platform.gameEvents().listen(event -> {
            long started = System.nanoTime();
            bus.publish(event);
            eventNanos += System.nanoTime() - started;
        });
        commands.registerReload(this::reloadAsync);
        commands.registerPerf(this::performance);
        platform.commands().register(COMMAND_ROOT, commands::handle);
        for (Feature feature : features) {
            feature.start(this);
            started.push(feature);
        }
        platform.log().info("Started features: " + features.stream().map(Feature::id).toList());
    }

    public void stop() {
        while (!started.isEmpty()) {
            Feature feature = started.pop();
            try {
                feature.stop();
            } catch (RuntimeException e) {
                platform.log().error("Feature failed to stop: " + feature.id(), e);
            }
        }
        storage.close();
    }

    /**
     * Перечитывает файлы в фоне и подменяет снимок на основном потоке.
     * При ошибке в файлах остаётся прежний конфиг.
     */
    public void reloadAsync(java.util.function.Consumer<Boolean> done) {
        platform.scheduler().supplyAsync(this::readConfig, snapshot -> {
            apply(snapshot);
            bus.publish(new ConfigReloaded());
            done.accept(true);
        }, error -> {
            platform.log().error("Config reload failed, keeping the previous one", error);
            done.accept(false);
        });
    }

    private record Snapshot(ConfigView config, Messages messages) {
    }

    private void loadConfig() {
        apply(readConfig());
    }

    private Snapshot readConfig() {
        ConfigView newConfig = YamlConfig.load(platform.configDirectory());
        return new Snapshot(newConfig,
                YamlMessages.load(platform.configDirectory(), newConfig.getString("language", "ru")));
    }

    private void apply(Snapshot snapshot) {
        messages = snapshot.messages();
        config = snapshot.config();
    }

    /** Сколько основного потока занял мод с прошлого запроса: задачи на тике и обработка событий игры. */
    private String performance() {
        Scheduler.TickStats stats = platform.scheduler().takeStats();
        long events = eventNanos;
        eventNanos = 0;
        int ticks = Math.max(1, stats.ticks());
        return messages.text("command.perf", stats.ticks(),
                micros(stats.totalNanos() / ticks), micros(stats.maxNanos()), micros(events / ticks));
    }

    private static String micros(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.1f", nanos / 1000.0);
    }

    @Override
    public Platform platform() {
        return platform;
    }

    @Override
    public Storage storage() {
        return storage;
    }

    @Override
    public EventBus bus() {
        return bus;
    }

    @Override
    public ConfigView config() {
        return config;
    }

    @Override
    public Messages messages() {
        return messages;
    }

    @Override
    public CommandRegistry commands() {
        return commands;
    }

    @Override
    public ServiceRegistry services() {
        return services;
    }

    @Override
    public Clock clock() {
        return clock;
    }
}
