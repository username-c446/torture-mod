package com.torturemod.testkit;

import com.torturemod.api.Platform;
import com.torturemod.api.feature.CommandRegistry;
import com.torturemod.api.feature.ConfigView;
import com.torturemod.api.feature.EventBus;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.feature.MapConfigView;
import com.torturemod.api.feature.Messages;
import com.torturemod.api.feature.ServiceRegistry;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.port.Commands;
import com.torturemod.api.port.Storage;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Контекст фичи для тестов. Тексты возвращаются как ключ и аргументы через пробел,
 * конфиг задаётся плоскими путями.
 */
public final class FakeContext implements FeatureContext {
    public final FakePlatform platform;
    public final InMemoryStorage storage;
    public final Map<String, Object> config = new HashMap<>();
    public final List<Object> published = new ArrayList<>();
    public final MutableClock clock = new MutableClock();

    private final Map<Class<?>, List<Consumer<Object>>> listeners = new HashMap<>();
    private final Map<String, Commands.Handler> commands = new HashMap<>();
    private final Map<String, String> commandPermissions = new HashMap<>();
    private final Map<Class<?>, Object> services = new HashMap<>();

    public FakeContext(FakePlatform platform) {
        this(platform, new InMemoryStorage());
    }

    public FakeContext(FakePlatform platform, InMemoryStorage storage) {
        this.platform = platform;
        this.storage = storage;
        platform.gameEvents().listen(this.bus()::publish);
    }

    /** Выполняет подкоманду /torturemod и возвращает ответы. */
    public List<String> run(Optional<TargetId> sender, String line) {
        List<String> parts = Arrays.asList(line.trim().split("\\s+"));
        Commands.Handler handler = commands.get(parts.get(0));
        if (handler == null) {
            throw new IllegalArgumentException("Unknown subcommand " + parts.get(0));
        }
        if (sender.isPresent() && !platform.permissions().has(sender.get(), commandPermissions.get(parts.get(0)))) {
            return List.of("command.no_permission");
        }
        List<String> replies = new ArrayList<>();
        handler.handle(new Commands.Invocation(sender, List.copyOf(parts.subList(1, parts.size())), replies::add));
        return replies;
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> published(Class<T> type) {
        return (List<T>) published.stream().filter(type::isInstance).toList();
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
        return new EventBus() {
            @Override
            public <T> Subscription subscribe(Class<T> eventType, Consumer<? super T> listener) {
                Consumer<Object> typed = event -> listener.accept(eventType.cast(event));
                List<Consumer<Object>> list = listeners.computeIfAbsent(eventType, key -> new CopyOnWriteArrayList<>());
                list.add(typed);
                return () -> list.remove(typed);
            }

            @Override
            public void publish(Object event) {
                published.add(event);
                listeners.getOrDefault(event.getClass(), List.of()).forEach(listener -> listener.accept(event));
            }
        };
    }

    @Override
    public ConfigView config() {
        return MapConfigView.fromFlat(config);
    }

    @Override
    public Messages messages() {
        return (key, args) -> args.length == 0
                ? key
                : key + " " + Arrays.stream(args).map(String::valueOf).collect(Collectors.joining(" "));
    }

    @Override
    public CommandRegistry commands() {
        return (name, permission, handler) -> {
            commands.put(name, handler);
            commandPermissions.put(name, permission);
        };
    }

    @Override
    public ServiceRegistry services() {
        return new ServiceRegistry() {
            @Override
            public <T> void provide(Class<T> type, T service) {
                services.put(type, service);
            }

            @Override
            public <T> Optional<T> find(Class<T> type) {
                return Optional.ofNullable(type.cast(services.get(type)));
            }
        };
    }

    @Override
    public Clock clock() {
        return clock;
    }

    /** Часы, которые двигаются только вручную. */
    public static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        public void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
