package com.torturemod.feature.cuffs;

import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.io.IOException;
import com.torturemod.api.resources.ResourcePackContents;
import com.torturemod.api.items.ShapedRecipe;
import com.torturemod.api.items.CustomItem;
import com.torturemod.api.captivity.CaptiveCaptured;
import com.torturemod.api.captivity.CaptiveReleased;
import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.cuffs.CuffsService;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.feature.EventBus;
import com.torturemod.api.feature.Feature;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.model.TargetKind;
import com.torturemod.api.port.Scheduler;
import com.torturemod.api.resistance.ResistanceService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Наручники: в них пленный ничего не делает руками, без них в тюрьме действует по флагам зон, а вне
 * тюрьмы свободно. Надеваются и снимаются за несколько нажатий. Нужны плен и сопротивление.
 */
public final class CuffsFeature implements Feature {
    /** Наручники запрещают раньше всех правил: их не отменяют флаги зон. */
    static final int CUFFED_RULE_PRIORITY = 100;
    /** Без наручников и без других правил пленный действует свободно. */
    static final int FREE_HANDS_RULE_PRIORITY = 1000;

    /** Наручники — цепь с меткой мода; крафт: цепь, железный слиток, цепь. */
    public static final String HANDCUFFS = "torturemod:handcuffs";

    private final List<EventBus.Subscription> subscriptions = new ArrayList<>();
    private Scheduler.Task tickTask;

    @Override
    public String id() {
        return "cuffs";
    }

    @Override
    public void start(FeatureContext context) {
        CaptivityService captivity = context.services().find(CaptivityService.class)
                .orElseThrow(() -> new IllegalStateException("Cuffs need the captivity feature started first"));
        ResistanceService resistance = context.services().find(ResistanceService.class)
                .orElseThrow(() -> new IllegalStateException("Cuffs need the resistance feature started first"));
        defineHandcuffs(context);
        Cuffs cuffs = new Cuffs(context, captivity);
        cuffs.load();
        CuffSessions sessions = new CuffSessions(context, captivity, resistance, cuffs);
        context.services().provide(CuffsService.class, cuffs);
        captivity.addActionRule(CUFFED_RULE_PRIORITY,
                (captive, action, where) -> cuffs.isCuffed(captive.target()) ? Optional.of(false) : Optional.empty());
        captivity.addActionRule(FREE_HANDS_RULE_PRIORITY, (captive, action, where) -> Optional.of(true));

        EventBus bus = context.bus();
        subscriptions.add(bus.subscribe(CaptiveCaptured.class, event -> {
            if (event.captive().kind() == TargetKind.PLAYER && context.config().getBoolean("cuffs.on_capture", true)) {
                cuffs.cuff(event.captive().target(), event.captive().captor());
            }
        }));
        subscriptions.add(bus.subscribe(CaptiveReleased.class, event -> {
            cuffs.forget(event.captive().target());
            sessions.forget(event.captive().target());
        }));
        subscriptions.add(bus.subscribe(GameEvent.EntityInteracted.class, sessions::onInteract));
        subscriptions.add(bus.subscribe(GameEvent.Damaged.class, sessions::onDamaged));
        tickTask = context.platform().scheduler().everyTick(sessions::tick);
    }

    /** Предмет, рецепт и модель для ресурспака. Без пака игрок видит цепь с названием «Наручники». */
    private static void defineHandcuffs(FeatureContext context) {
        int model = context.config().getInt("cuffs.custom_model_data", 740001);
        context.platform().items().defineCustomItem(new CustomItem(HANDCUFFS, "minecraft:chain", model,
                context.messages().text("cuffs.item_name"), List.of(context.messages().text("cuffs.item_lore"))));
        context.platform().items().addRecipe(new ShapedRecipe(HANDCUFFS, List.of("CIC"),
                Map.of('C', "minecraft:chain", 'I', "minecraft:iron_ingot"), HANDCUFFS, 1));
        context.services().find(ResourcePackContents.class).ifPresent(pack -> {
            try (InputStream texture = CuffsFeature.class.getResourceAsStream("/torturemod/resourcepack/handcuffs.png")) {
                if (texture != null) {
                    pack.add("assets/torturemod/textures/item/handcuffs.png", texture.readAllBytes());
                }
            } catch (IOException e) {
                context.platform().log().error("Cannot read the handcuffs texture", e);
            }
            pack.add("assets/torturemod/models/item/handcuffs.json", ("{\"parent\":\"minecraft:item/generated\","
                    + "\"textures\":{\"layer0\":\"torturemod:item/handcuffs\"}}").getBytes(StandardCharsets.UTF_8));
            pack.overrideItemModel("minecraft:chain", model, "torturemod:item/handcuffs");
        });
    }

    @Override
    public void stop() {
        subscriptions.forEach(EventBus.Subscription::cancel);
        subscriptions.clear();
        if (tickTask != null) {
            tickTask.cancel();
        }
    }
}
