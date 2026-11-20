package com.torturemod.feature.prison;

import com.torturemod.api.captivity.CaptiveCaptured;
import com.torturemod.api.captivity.CaptiveReleased;
import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.feature.ConfigReloaded;
import com.torturemod.api.feature.EventBus;
import com.torturemod.api.feature.Feature;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.port.Permissions;
import com.torturemod.api.port.Scheduler;
import com.torturemod.api.prison.PrisonService;
import com.torturemod.api.resistance.ResistanceService;
import com.torturemod.api.torture.CaptiveMenuExtensions;
import com.torturemod.api.torture.MenuButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Тюрьма поверх клеток: разметка без команд, зоны с флагами, команда с ролями, побег за границы
 * с тревогой охране, срок, норма с наградами и выкуп. Предоставляет {@link PrisonService}.
 * Нужны плен и сопротивление; кнопка в меню пленного появляется, если запущены пытки.
 */
public final class PrisonFeature implements Feature {
    private final List<EventBus.Subscription> subscriptions = new ArrayList<>();
    private Scheduler.Task tickTask;
    private volatile PrisonSettings settings;

    @Override
    public String id() {
        return "prison";
    }

    @Override
    public void start(FeatureContext context) {
        CaptivityService captivity = context.services().find(CaptivityService.class)
                .orElseThrow(() -> new IllegalStateException("Prison needs the captivity feature started first"));
        ResistanceService resistance = context.services().find(ResistanceService.class)
                .orElseThrow(() -> new IllegalStateException("Prison needs the resistance feature started first"));
        settings = PrisonSettings.from(context.config());
        PrisonRegistry registry = new PrisonRegistry(context, () -> settings);
        registry.load(captivity);
        Support support = new Support(context, registry, captivity, () -> settings);
        Deposits deposits = new Deposits(support, resistance);
        Warden warden = new Warden(support, deposits);
        Marking marking = new Marking(support);
        Ransom ransom = new Ransom(support);
        ChatPrompts prompts = new ChatPrompts(support);
        ConsoleMenus console = new ConsoleMenus(support, prompts, ransom, deposits);
        context.services().provide(PrisonService.class, registry);
        captivity.addContainment(warden);
        captivity.addAccessRule(warden::access);
        captivity.addActionRule(Warden.ZONE_RULE_PRIORITY, warden::act);
        context.services().find(CaptiveMenuExtensions.class).ifPresent(menu -> menu.add((viewer, captive) ->
                registry.ofInmate(captive).filter(prison -> registry.isTeam(prison, viewer))
                        .map(prison -> new MenuButton("minecraft:iron_door", support.text("prison.captive_button"),
                                List.of(prison.name), () -> console.openInmate(viewer, prison, captive)))));

        EventBus bus = context.bus();
        on(bus, ConfigReloaded.class, event -> settings = PrisonSettings.from(context.config()));
        on(bus, CaptiveCaptured.class, event -> warden.onCaptured(event.captive()));
        on(bus, CaptiveReleased.class, event -> warden.onReleased(event.captive().target()));
        on(bus, GameEvent.BlockAttacked.class, marking::onAttack);
        on(bus, GameEvent.ArmSwung.class, marking::onSwing);
        on(bus, GameEvent.BlockInteracted.class, event -> {
            marking.onBlockUse(event);
            console.onBlockUse(event);
            deposits.onBlockUse(event);
            ransom.onBlockUse(event);
        });
        on(bus, GameEvent.ItemUsed.class, marking::onItemUse);
        on(bus, GameEvent.EntityInteracted.class, warden::onInteract);
        on(bus, GameEvent.Damaged.class, warden::onDamaged);
        on(bus, GameEvent.ChatSent.class, prompts::onChat);
        on(bus, GameEvent.PlayerLeft.class, event -> marking.forget(event.player()));
        tickTask = context.platform().scheduler().everyTick(() -> {
            marking.tick();
            warden.tick();
            ransom.tick();
        });
        context.commands().register("prison", Permissions.USE, invocation -> {
            Consumer<String> reply = invocation.reply();
            Optional<com.torturemod.api.model.TargetId> sender = invocation.sender();
            if (!invocation.args().isEmpty() && invocation.args().get(0).equals("delete")) {
                deleteAt(context, registry, console, support, invocation);
                return;
            }
            if (sender.isEmpty()) {
                reply.accept(support.text("prison.command.console", registry.prisons.size()));
                registry.prisons.values().forEach(prison -> reply.accept(prison.name + " — "
                        + support.name(prison.owner) + ", " + prison.area.worldId() + " " + prison.area.minX() + " "
                        + prison.area.minY() + " " + prison.area.minZ()));
                return;
            }
            console.openByCommand(sender.get(), reply);
        });
    }

    /** /torturemod prison delete &lt;мир&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt;: администратор удаляет тюрьму, в которой этот блок. */
    private static void deleteAt(FeatureContext context, PrisonRegistry registry, ConsoleMenus console, Support support,
                                 com.torturemod.api.port.Commands.Invocation invocation) {
        if (invocation.sender().isPresent()
                && !context.platform().permissions().has(invocation.sender().get(), Permissions.ADMIN)) {
            invocation.reply().accept(support.text("command.no_permission"));
            return;
        }
        List<String> args = invocation.args();
        Optional<Prison> prison = Optional.empty();
        try {
            if (args.size() == 5) {
                prison = registry.at(new com.torturemod.api.model.BlockLocation(args.get(1),
                        Integer.parseInt(args.get(2)), Integer.parseInt(args.get(3)), Integer.parseInt(args.get(4))));
            }
        } catch (NumberFormatException ignored) {
            // Ответ ниже подскажет формат.
        }
        if (prison.isEmpty()) {
            invocation.reply().accept(support.text("prison.command.delete_usage"));
            return;
        }
        console.delete(prison.get());
        invocation.reply().accept(support.text("prison.settings.deleted", prison.get().name));
    }

    @Override
    public void stop() {
        subscriptions.forEach(EventBus.Subscription::cancel);
        subscriptions.clear();
        if (tickTask != null) {
            tickTask.cancel();
        }
    }

    private <T> void on(EventBus bus, Class<T> type, Consumer<? super T> listener) {
        subscriptions.add(bus.subscribe(type, listener));
    }
}
