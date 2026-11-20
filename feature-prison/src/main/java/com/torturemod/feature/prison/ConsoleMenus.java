package com.torturemod.feature.prison;

import com.torturemod.api.captivity.Captive;
import com.torturemod.api.cuffs.CuffsService;
import com.torturemod.api.event.GameEvent;
import com.torturemod.api.model.ItemStackData;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetKind;
import com.torturemod.api.model.TargetState;
import com.torturemod.api.port.Menus;
import com.torturemod.api.prison.ChestRole;
import com.torturemod.api.prison.ConsoleSection;
import com.torturemod.api.prison.PrisonRight;
import com.torturemod.api.torture.MenuButton;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Пульт тюрьмы: пленные, зоны и флаги, роли, норма и награды, сделки, настройки и разделы других фич. */
final class ConsoleMenus {
    private static final List<Integer> SECTION_SLOTS = List.of(16, 19, 20, 21, 22, 23, 24, 25);
    private static final long SENTENCE_STEP_SMALL = 10 * 60_000L;
    private static final long SENTENCE_STEP_LARGE = 60 * 60_000L;

    private final Support s;
    private final ChatPrompts prompts;
    private final Ransom ransom;
    private final Deposits deposits;
    /** Кто нажал «удалить тюрьму» один раз: второе нажатие удаляет. */
    private final java.util.Set<TargetId> deleteArmed = new java.util.HashSet<>();

    ConsoleMenus(Support support, ChatPrompts prompts, Ransom ransom, Deposits deposits) {
        this.s = support;
        this.prompts = prompts;
        this.ransom = ransom;
        this.deposits = deposits;
    }

    /** ПКМ по кафедре-пульту: команда открывает меню, остальным кафедра работает как обычно. */
    void onBlockUse(GameEvent.BlockInteracted event) {
        if (event.cancellation().isCancelled() || event.sneaking() || s.captivity.isCaptive(event.actor())) {
            return;
        }
        Optional<Prison> prison = s.registry.at(event.block()).filter(found -> found.consoles.contains(event.block()));
        if (prison.isEmpty() || !s.registry.isTeam(prison.get(), event.actor())) {
            return;
        }
        event.cancellation().cancel();
        openMain(event.actor(), prison.get());
    }

    /** Запасной путь: /torturemod prison внутри своей тюрьмы или для единственной своей. */
    void openByCommand(TargetId player, Consumer<String> reply) {
        Optional<TargetState> state = s.context.platform().targets().state(player);
        Optional<Prison> here = state.flatMap(found -> s.registry.at(found.location()))
                .filter(prison -> s.registry.isTeam(prison, player));
        if (here.isPresent()) {
            openMain(player, here.get());
            return;
        }
        List<Prison> own = s.registry.prisons.values().stream().filter(prison -> s.registry.isTeam(prison, player)).toList();
        if (own.size() == 1) {
            openMain(player, own.get(0));
        } else {
            reply.accept(s.text(own.isEmpty() ? "prison.command.none" : "prison.command.go_inside"));
        }
    }

    // ---------- главное меню ----------

    void openMain(TargetId viewer, Prison prison) {
        List<Menus.Entry> entries = new ArrayList<>();
        long inmates = s.registry.inmates.values().stream().filter(inmate -> inmate.prisonId.equals(prison.id)).count();
        String role = s.registry.roleOf(prison, viewer).map(s::roleName).orElse("-");
        entries.add(entry(4, "minecraft:lodestone", prison.name, List.of(
                s.text("prison.menu.owner", s.name(prison.owner)),
                s.text("prison.menu.size", prison.area.sizeX(), prison.area.sizeY(), prison.area.sizeZ()),
                s.text("prison.menu.counts", inmates, prison.zones.size(), prison.members.size() + 1),
                s.text("prison.menu.your_role", role)), null));
        entries.add(section(viewer, prison, 10, "minecraft:iron_bars", "prison.menu.inmates", null,
                () -> openInmates(viewer, prison)));
        entries.add(section(viewer, prison, 11, "minecraft:filled_map", "prison.menu.zones", PrisonRight.MANAGE,
                () -> openZones(viewer, prison)));
        entries.add(section(viewer, prison, 12, "minecraft:golden_helmet", "prison.menu.roles", PrisonRight.ROLES,
                () -> openRoles(viewer, prison)));
        entries.add(section(viewer, prison, 13, "minecraft:chest", "prison.menu.quota", PrisonRight.QUOTA,
                () -> openQuota(viewer, prison)));
        long pending = s.registry.deals.values().stream()
                .filter(deal -> deal.prisonId.equals(prison.id) && deal.status == Deal.Status.PENDING).count();
        Menus.Entry deals = section(viewer, prison, 14, "minecraft:emerald", "prison.menu.deals", PrisonRight.DEALS,
                () -> openDeals(viewer, prison));
        entries.add(new Menus.Entry(deals.slot(), deals.itemId(), deals.label() + (pending > 0 ? " (" + pending + ")" : ""),
                deals.lore(), deals.onClick()));
        entries.add(section(viewer, prison, 15, "minecraft:comparator", "prison.menu.settings", PrisonRight.MANAGE,
                () -> openSettings(viewer, prison)));
        int index = 0;
        for (ConsoleSection section : s.registry.sections) {
            Optional<MenuButton> button = section.button(prison.id, viewer);
            if (button.isPresent() && index < SECTION_SLOTS.size()) {
                entries.add(entry(SECTION_SLOTS.get(index++), button.get().itemId(), button.get().label(),
                        button.get().lore(), button.get().onClick()));
            }
        }
        open(viewer, s.text("prison.menu.title", prison.name), 3, entries);
        s.hint(viewer, "hint.prison.menu");
    }

    private Menus.Entry section(TargetId viewer, Prison prison, int slot, String icon, String key, PrisonRight right,
                                Runnable action) {
        boolean allowed = right == null || s.registry.has(prison, viewer, right);
        return allowed ? entry(slot, icon, s.text(key), List.of(s.text(key + ".lore")), action)
                : entry(slot, "minecraft:gray_stained_glass_pane", s.text(key), List.of(s.text("prison.menu.no_right")), null);
    }

    // ---------- пленные ----------

    void openInmates(TargetId viewer, Prison prison) {
        List<Menus.Entry> entries = new ArrayList<>();
        int slot = 0;
        for (Map.Entry<TargetId, Inmate> entry : s.registry.inmates.entrySet()) {
            if (!entry.getValue().prisonId.equals(prison.id) || slot >= 45) {
                continue;
            }
            TargetId captive = entry.getKey();
            entries.add(entry(slot++, icon(captive), s.name(captive), inmateLore(prison, captive, entry.getValue()),
                    () -> openInmate(viewer, prison, captive)));
        }
        if (slot == 0) {
            entries.add(entry(22, "minecraft:barrier", s.text("prison.menu.no_inmates"), List.of(), null));
        }
        entries.add(back(49, () -> openMain(viewer, prison)));
        open(viewer, s.text("prison.menu.inmates"), 6, entries);
    }

    void openInmate(TargetId viewer, Prison prison, TargetId captive) {
        Inmate inmate = s.registry.inmates.get(captive);
        if (inmate == null || !inmate.prisonId.equals(prison.id)) {
            openInmates(viewer, prison);
            return;
        }
        List<Menus.Entry> entries = new ArrayList<>();
        entries.add(entry(4, icon(captive), s.name(captive), inmateLore(prison, captive, inmate), null));
        if (s.registry.has(prison, viewer, PrisonRight.SENTENCE)) {
            entries.add(entry(10, "minecraft:red_dye", s.text("prison.inmate.sentence_minus", 60), List.of(),
                    () -> changeSentence(viewer, prison, captive, -SENTENCE_STEP_LARGE)));
            entries.add(entry(11, "minecraft:pink_dye", s.text("prison.inmate.sentence_minus", 10), List.of(),
                    () -> changeSentence(viewer, prison, captive, -SENTENCE_STEP_SMALL)));
            entries.add(entry(12, "minecraft:clock", sentenceText(inmate), List.of(s.text("prison.inmate.sentence_lore")), null));
            entries.add(entry(13, "minecraft:lime_dye", s.text("prison.inmate.sentence_plus", 10), List.of(),
                    () -> changeSentence(viewer, prison, captive, SENTENCE_STEP_SMALL)));
            entries.add(entry(14, "minecraft:green_dye", s.text("prison.inmate.sentence_plus", 60), List.of(),
                    () -> changeSentence(viewer, prison, captive, SENTENCE_STEP_LARGE)));
            entries.add(entry(15, "minecraft:barrier", s.text("prison.inmate.sentence_clear"), List.of(), () -> {
                inmate.sentenceLeftMillis = -1;
                s.registry.saveInmate(captive);
                openInmate(viewer, prison, captive);
            }));
        }
        if (s.registry.has(prison, viewer, PrisonRight.PRICE)) {
            entries.add(entry(19, "minecraft:gold_ingot", s.text("prison.inmate.price_set"),
                    List.of(s.text("prison.ransom.price", s.items(inmate.price)), s.text("prison.samples.lore")),
                    () -> samples(viewer, s.text("prison.inmate.price_set"), counts -> {
                        inmate.price.clear();
                        inmate.price.putAll(counts);
                        s.registry.saveInmate(captive);
                        s.message(viewer, "prison.inmate.price_saved", s.name(captive), s.items(counts));
                    })));
            entries.add(entry(20, "minecraft:gray_dye", s.text("prison.inmate.price_clear"), List.of(), () -> {
                inmate.price.clear();
                s.registry.saveInmate(captive);
                openInmate(viewer, prison, captive);
            }));
        }
        if (s.registry.has(prison, viewer, PrisonRight.QUOTA)) {
            entries.add(entry(22, "minecraft:iron_pickaxe", s.text("prison.inmate.quota_set"),
                    List.of(s.text("prison.quota.current", s.items(inmate.quotaOverride)), s.text("prison.samples.lore")),
                    () -> samples(viewer, s.text("prison.inmate.quota_set"), counts -> {
                        inmate.quotaOverride.clear();
                        inmate.quotaOverride.putAll(counts);
                        s.registry.saveInmate(captive);
                        s.message(viewer, "prison.quota.saved", s.items(counts));
                    })));
            entries.add(entry(23, "minecraft:gray_dye", s.text("prison.inmate.quota_clear"), List.of(), () -> {
                inmate.quotaOverride.clear();
                s.registry.saveInmate(captive);
                openInmate(viewer, prison, captive);
            }));
        }
        entries.add(back(18, () -> openInmates(viewer, prison)));
        open(viewer, s.text("prison.inmate.title", s.name(captive)), 3, entries);
    }

    private void changeSentence(TargetId viewer, Prison prison, TargetId captive, long delta) {
        Inmate inmate = s.registry.inmates.get(captive);
        if (inmate == null) {
            return;
        }
        long max = s.settings().maxSentenceMinutes() * 60_000L;
        long current = Math.max(0, inmate.sentenceLeftMillis);
        long next = Math.min(max, current + delta);
        inmate.sentenceLeftMillis = next <= 0 ? -1 : next;
        s.registry.saveInmate(captive);
        if (inmate.hasSentence()) {
            s.message(captive, "prison.sentence.changed", Support.minutes(inmate.sentenceLeftMillis));
        }
        openInmate(viewer, prison, captive);
    }

    private List<String> inmateLore(Prison prison, TargetId captive, Inmate inmate) {
        List<String> lore = new ArrayList<>();
        lore.add(sentenceText(inmate));
        Map<String, Integer> quota = inmate.quotaOverride.isEmpty() ? prison.quota : inmate.quotaOverride;
        lore.add(s.text("prison.inmate.quota", deposits.progress(inmate, quota)));
        lore.add(s.text("prison.ransom.price", s.items(inmate.price)));
        Optional<CuffsService> cuffs = s.context.services().find(CuffsService.class);
        if (cuffs.isPresent()) {
            lore.add(s.text(cuffs.get().isCuffed(captive) ? "prison.inmate.cuffed" : "prison.inmate.uncuffed"));
        }
        s.context.platform().targets().state(captive).ifPresent(state -> lore.add(s.text("prison.inmate.where",
                Math.round(state.location().x()) + " " + Math.round(state.location().y()) + " "
                        + Math.round(state.location().z()))));
        return lore;
    }

    private String sentenceText(Inmate inmate) {
        return inmate.hasSentence() ? s.text("prison.inmate.sentence", Support.minutes(inmate.sentenceLeftMillis))
                : s.text("prison.inmate.no_sentence");
    }

    private String icon(TargetId captive) {
        Optional<Captive> found = s.captivity.find(captive);
        if (found.isPresent() && found.get().kind() == TargetKind.MOB) {
            return s.context.platform().targets().state(captive).map(state -> state.typeId() + "_spawn_egg")
                    .orElse("minecraft:name_tag");
        }
        return "minecraft:player_head";
    }

    // ---------- зоны и флаги ----------

    void openZones(TargetId viewer, Prison prison) {
        List<Menus.Entry> entries = new ArrayList<>();
        for (int i = 0; i < prison.zones.size() && i < 45; i++) {
            Zone zone = prison.zones.get(i);
            int index = i;
            entries.add(entry(i, "minecraft:filled_map", zone.name, List.of(
                    s.text("prison.zone.area", zone.area.sizeX(), zone.area.sizeY(), zone.area.sizeZ()),
                    s.text("prison.zone.priority", zone.priority),
                    s.text("prison.zone.flags", flagList(zone.flags)),
                    s.text("prison.quota.current", s.items(zone.quota))), () -> openZone(viewer, prison, index)));
        }
        entries.add(entry(45, "minecraft:grass_block", s.text("prison.zone.base"),
                List.of(s.text("prison.zone.flags", flagList(prison.baseFlags)), s.text("prison.zone.base_lore")),
                () -> openFlags(viewer, prison, prison.baseFlags, s.text("prison.zone.base"), () -> openZones(viewer, prison))));
        entries.add(entry(47, "minecraft:stick", s.text("prison.zone.how"), List.of(s.text("prison.zone.how_lore")), null));
        entries.add(back(49, () -> openMain(viewer, prison)));
        open(viewer, s.text("prison.menu.zones"), 6, entries);
    }

    private void openZone(TargetId viewer, Prison prison, int index) {
        if (index >= prison.zones.size()) {
            openZones(viewer, prison);
            return;
        }
        Zone zone = prison.zones.get(index);
        List<Menus.Entry> entries = new ArrayList<>();
        entries.add(entry(4, "minecraft:filled_map", zone.name, List.of(
                s.text("prison.zone.area", zone.area.sizeX(), zone.area.sizeY(), zone.area.sizeZ()),
                s.text("prison.zone.priority", zone.priority)), null));
        addFlagToggles(entries, viewer, prison, zone.flags, () -> openZone(viewer, prison, index));
        entries.add(back(18, () -> openZones(viewer, prison)));
        entries.add(entry(19, "minecraft:red_dye", s.text("prison.zone.priority_minus"), List.of(), () -> {
            zone.priority--;
            s.registry.save(prison);
            openZone(viewer, prison, index);
        }));
        entries.add(entry(20, "minecraft:lime_dye", s.text("prison.zone.priority_plus"), List.of(), () -> {
            zone.priority++;
            s.registry.save(prison);
            openZone(viewer, prison, index);
        }));
        entries.add(entry(22, "minecraft:name_tag", s.text("prison.zone.rename"), List.of(), () ->
                prompts.ask(viewer, "prison.prompt.zone_name", name -> {
                    zone.name = name;
                    s.registry.save(prison);
                    s.message(viewer, "prison.zone.renamed", name);
                })));
        entries.add(entry(23, "minecraft:iron_pickaxe", s.text("prison.zone.quota_set"),
                List.of(s.text("prison.quota.current", s.items(zone.quota)), s.text("prison.samples.lore")),
                () -> samples(viewer, s.text("prison.zone.quota_set"), counts -> {
                    zone.quota.clear();
                    zone.quota.putAll(counts);
                    s.registry.save(prison);
                    s.message(viewer, "prison.quota.saved", s.items(counts));
                })));
        entries.add(entry(24, "minecraft:gray_dye", s.text("prison.zone.quota_clear"), List.of(), () -> {
            zone.quota.clear();
            s.registry.save(prison);
            openZone(viewer, prison, index);
        }));
        entries.add(entry(26, "minecraft:tnt", s.text("prison.zone.delete"), List.of(), () -> {
            prison.zones.remove(zone);
            s.registry.save(prison);
            s.message(viewer, "prison.zone.deleted", zone.name);
            openZones(viewer, prison);
        }));
        open(viewer, s.text("prison.zone.title", zone.name), 3, entries);
    }

    private void openFlags(TargetId viewer, Prison prison, java.util.Set<Flag> flags, String title, Runnable back) {
        List<Menus.Entry> entries = new ArrayList<>();
        addFlagToggles(entries, viewer, prison, flags, () -> openFlags(viewer, prison, flags, title, back));
        entries.add(back(18, back));
        open(viewer, title, 3, entries);
    }

    private void addFlagToggles(List<Menus.Entry> entries, TargetId viewer, Prison prison, java.util.Set<Flag> flags,
                                Runnable reopen) {
        int slot = 10;
        for (Flag flag : Flag.values()) {
            boolean on = flags.contains(flag);
            entries.add(entry(slot++, on ? "minecraft:lime_dye" : "minecraft:gray_dye",
                    s.text("prison.flag." + flag.name()) + ": " + s.text(on ? "prison.on" : "prison.off"),
                    List.of(s.text("prison.flag." + flag.name() + ".lore")), () -> {
                        if (!flags.remove(flag)) {
                            flags.add(flag);
                        }
                        s.registry.save(prison);
                        reopen.run();
                    }));
        }
    }

    private String flagList(java.util.Set<Flag> flags) {
        if (flags.isEmpty()) {
            return s.text("prison.items.none");
        }
        return String.join(", ", flags.stream().map(flag -> s.text("prison.flag." + flag.name())).toList());
    }

    // ---------- роли ----------

    void openRoles(TargetId viewer, Prison prison) {
        List<Menus.Entry> entries = new ArrayList<>();
        entries.add(entry(0, "minecraft:player_head", s.name(prison.owner),
                List.of(s.text("prison.role.owner")), null));
        int slot = 1;
        for (Map.Entry<TargetId, String> member : prison.members.entrySet()) {
            if (slot >= 45) {
                break;
            }
            TargetId player = member.getKey();
            String role = prison.role(member.getValue()).map(s::roleName).orElse(member.getValue());
            entries.add(entry(slot++, "minecraft:player_head", s.name(player), List.of(role),
                    () -> openMember(viewer, prison, player)));
        }
        entries.add(entry(45, "minecraft:emerald", s.text("prison.roles.add"), List.of(),
                () -> openAddMember(viewer, prison)));
        entries.add(entry(47, "minecraft:writable_book", s.text("prison.roles.list"), List.of(),
                () -> openRoleList(viewer, prison)));
        entries.add(back(49, () -> openMain(viewer, prison)));
        open(viewer, s.text("prison.menu.roles"), 6, entries);
    }

    private void openAddMember(TargetId viewer, Prison prison) {
        List<Menus.Entry> entries = new ArrayList<>();
        int slot = 0;
        for (TargetState player : s.context.platform().players().online()) {
            if (slot >= 45 || s.registry.isTeam(prison, player.id()) || s.captivity.isCaptive(player.id())) {
                continue;
            }
            entries.add(entry(slot++, "minecraft:player_head", player.name(), List.of(),
                    () -> openPickRole(viewer, prison, player.id())));
        }
        if (slot == 0) {
            entries.add(entry(22, "minecraft:barrier", s.text("prison.roles.nobody"), List.of(), null));
        }
        entries.add(back(49, () -> openRoles(viewer, prison)));
        open(viewer, s.text("prison.roles.add"), 6, entries);
    }

    private void openPickRole(TargetId viewer, Prison prison, TargetId player) {
        List<Menus.Entry> entries = new ArrayList<>();
        int slot = 0;
        for (Role role : prison.allRoles()) {
            if (role.id.equals(Role.OWNER) || slot >= 18) {
                continue;
            }
            entries.add(entry(slot++, "minecraft:paper", s.roleName(role), rightsLore(role), () -> {
                prison.members.put(player, role.id);
                s.registry.save(prison);
                s.message(viewer, "prison.roles.set", s.name(player), s.roleName(role));
                s.message(player, "prison.roles.you_are", s.roleName(role), prison.name);
                openRoles(viewer, prison);
            }));
        }
        entries.add(back(22, () -> openRoles(viewer, prison)));
        open(viewer, s.text("prison.roles.pick", s.name(player)), 3, entries);
    }

    private void openMember(TargetId viewer, Prison prison, TargetId player) {
        List<Menus.Entry> entries = new ArrayList<>();
        String role = prison.role(prison.members.getOrDefault(player, "")).map(s::roleName).orElse("-");
        entries.add(entry(4, "minecraft:player_head", s.name(player), List.of(role), null));
        entries.add(entry(10, "minecraft:paper", s.text("prison.roles.change"), List.of(),
                () -> openPickRole(viewer, prison, player)));
        entries.add(entry(12, "minecraft:barrier", s.text("prison.roles.remove"), List.of(), () -> {
            prison.members.remove(player);
            s.registry.save(prison);
            s.message(viewer, "prison.roles.removed", s.name(player));
            openRoles(viewer, prison);
        }));
        if (prison.owner.equals(viewer)) {
            entries.add(entry(14, "minecraft:nether_star", s.text("prison.roles.transfer"),
                    List.of(s.text("prison.roles.transfer_lore")), () -> {
                        prison.members.remove(player);
                        prison.members.put(prison.owner, Role.COOWNER);
                        prison.owner = player;
                        s.registry.save(prison);
                        s.message(viewer, "prison.roles.transferred", s.name(player));
                        s.message(player, "prison.roles.you_own", prison.name);
                        s.context.platform().menus().close(viewer);
                    }));
        }
        entries.add(back(18, () -> openRoles(viewer, prison)));
        open(viewer, s.name(player), 3, entries);
    }

    private void openRoleList(TargetId viewer, Prison prison) {
        List<Menus.Entry> entries = new ArrayList<>();
        int slot = 0;
        for (Role role : prison.allRoles()) {
            if (slot >= 45) {
                break;
            }
            List<String> lore = new ArrayList<>(rightsLore(role));
            if (role.preset) {
                lore.add(s.text("prison.roles.preset"));
            }
            entries.add(entry(slot++, role.preset ? "minecraft:book" : "minecraft:writable_book", s.roleName(role), lore,
                    role.preset ? null : () -> openRoleEdit(viewer, prison, role)));
        }
        entries.add(entry(45, "minecraft:emerald", s.text("prison.roles.create"), List.of(), () ->
                prompts.ask(viewer, "prison.prompt.role_name", name -> {
                    Role role = new Role("custom-" + UUID.randomUUID().toString().substring(0, 8), name,
                            EnumSet.noneOf(PrisonRight.class), false);
                    prison.customRoles.put(role.id, role);
                    s.registry.save(prison);
                    s.message(viewer, "prison.roles.created", name);
                    openRoleEdit(viewer, prison, role);
                })));
        entries.add(back(49, () -> openRoles(viewer, prison)));
        open(viewer, s.text("prison.roles.list"), 6, entries);
    }

    private void openRoleEdit(TargetId viewer, Prison prison, Role role) {
        List<Menus.Entry> entries = new ArrayList<>();
        int slot = 0;
        for (PrisonRight right : PrisonRight.values()) {
            if (right == PrisonRight.DELETE) {
                continue;
            }
            boolean on = role.rights.contains(right);
            entries.add(entry(slot++, on ? "minecraft:lime_dye" : "minecraft:gray_dye",
                    s.rightName(right) + ": " + s.text(on ? "prison.on" : "prison.off"), List.of(), () -> {
                        if (!role.rights.remove(right)) {
                            role.rights.add(right);
                        }
                        s.registry.save(prison);
                        openRoleEdit(viewer, prison, role);
                    }));
        }
        entries.add(back(18, () -> openRoleList(viewer, prison)));
        entries.add(entry(22, "minecraft:name_tag", s.text("prison.roles.rename"), List.of(), () ->
                prompts.ask(viewer, "prison.prompt.role_name", name -> {
                    role.name = name;
                    s.registry.save(prison);
                    s.message(viewer, "prison.roles.renamed", name);
                })));
        entries.add(entry(26, "minecraft:tnt", s.text("prison.roles.delete"), List.of(s.text("prison.roles.delete_lore")),
                () -> {
                    prison.customRoles.remove(role.id);
                    prison.members.replaceAll((player, id) -> id.equals(role.id) ? Role.WARDEN : id);
                    s.registry.save(prison);
                    openRoleList(viewer, prison);
                }));
        open(viewer, s.roleName(role), 3, entries);
    }

    private List<String> rightsLore(Role role) {
        List<String> lore = new ArrayList<>();
        for (PrisonRight right : role.rights) {
            lore.add("• " + s.rightName(right));
        }
        return lore.isEmpty() ? List.of(s.text("prison.roles.no_rights")) : lore;
    }

    // ---------- норма и награды ----------

    void openQuota(TargetId viewer, Prison prison) {
        List<Menus.Entry> entries = new ArrayList<>();
        entries.add(entry(4, "minecraft:chest", s.text("prison.menu.quota"), List.of(
                s.text("prison.quota.current", s.items(prison.quota)),
                s.text("prison.quota.day", s.settings().dayMillis() / 60_000),
                s.text("prison.quota.chests", prison.chests(ChestRole.DEPOSIT).size(),
                        prison.chests(ChestRole.REWARDS).size())), null));
        entries.add(entry(10, "minecraft:iron_pickaxe", s.text("prison.quota.set"), List.of(s.text("prison.samples.lore")),
                () -> samples(viewer, s.text("prison.quota.set"), counts -> {
                    prison.quota.clear();
                    prison.quota.putAll(counts);
                    s.registry.save(prison);
                    s.message(viewer, "prison.quota.saved", s.items(counts));
                })));
        entries.add(entry(11, "minecraft:gray_dye", s.text("prison.quota.clear"), List.of(), () -> {
            prison.quota.clear();
            s.registry.save(prison);
            openQuota(viewer, prison);
        }));
        entries.add(entry(13, "minecraft:red_dye", s.text("prison.reward.sentence_minus"), List.of(), () -> {
            prison.rewardSentencePercent = Math.max(0, prison.rewardSentencePercent - 5);
            s.registry.save(prison);
            openQuota(viewer, prison);
        }));
        entries.add(entry(14, "minecraft:clock", s.text("prison.reward.sentence_value", prison.rewardSentencePercent),
                List.of(s.text("prison.reward.sentence_lore")), null));
        entries.add(entry(15, "minecraft:lime_dye", s.text("prison.reward.sentence_plus"), List.of(), () -> {
            prison.rewardSentencePercent = Math.min(90, prison.rewardSentencePercent + 5);
            s.registry.save(prison);
            openQuota(viewer, prison);
        }));
        entries.add(entry(19, "minecraft:red_dye", s.text("prison.reward.recovery_minus"), List.of(), () -> {
            prison.rewardRecoveryMultiplier = Math.max(0, round(prison.rewardRecoveryMultiplier - 0.5));
            s.registry.save(prison);
            openQuota(viewer, prison);
        }));
        entries.add(entry(20, "minecraft:golden_apple", s.text("prison.reward.recovery_value", prison.rewardRecoveryMultiplier),
                List.of(s.text("prison.reward.recovery_lore")), null));
        entries.add(entry(21, "minecraft:lime_dye", s.text("prison.reward.recovery_plus"), List.of(), () -> {
            prison.rewardRecoveryMultiplier = Math.min(5, round(prison.rewardRecoveryMultiplier + 0.5));
            s.registry.save(prison);
            openQuota(viewer, prison);
        }));
        entries.add(entry(23, "minecraft:bread", s.text("prison.reward.items_set"),
                List.of(s.text("prison.reward.items", s.items(prison.rewardItems)), s.text("prison.samples.lore")),
                () -> samples(viewer, s.text("prison.reward.items_set"), counts -> {
                    prison.rewardItems.clear();
                    prison.rewardItems.putAll(counts);
                    s.registry.save(prison);
                    s.message(viewer, "prison.reward.items_saved", s.items(counts));
                })));
        entries.add(entry(24, "minecraft:gray_dye", s.text("prison.reward.items_clear"), List.of(), () -> {
            prison.rewardItems.clear();
            s.registry.save(prison);
            openQuota(viewer, prison);
        }));
        entries.add(back(18, () -> openMain(viewer, prison)));
        open(viewer, s.text("prison.menu.quota"), 3, entries);
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    // ---------- сделки ----------

    void openDeals(TargetId viewer, Prison prison) {
        List<Menus.Entry> entries = new ArrayList<>();
        int slot = 0;
        for (Deal deal : List.copyOf(s.registry.deals.values())) {
            if (!deal.prisonId.equals(prison.id) || deal.status != Deal.Status.PENDING || slot >= 45) {
                continue;
            }
            Map<String, Integer> counts = new java.util.LinkedHashMap<>();
            deal.escrow.forEach(stack -> counts.merge(stack.itemId(), stack.count(), Integer::sum));
            entries.add(entry(slot++, "minecraft:paper", s.text("prison.deal.entry", s.name(deal.captive), s.name(deal.buyer)),
                    List.of(s.text("prison.ransom.escrow", s.items(counts))), () -> openDeal(viewer, prison, deal)));
        }
        if (slot == 0) {
            entries.add(entry(22, "minecraft:barrier", s.text("prison.deal.none"), List.of(), null));
        }
        entries.add(entry(47, prison.confirmDeals ? "minecraft:lime_dye" : "minecraft:gray_dye",
                s.text("prison.deal.confirm") + ": " + s.text(prison.confirmDeals ? "prison.on" : "prison.off"),
                List.of(s.text("prison.deal.confirm_lore")), () -> {
                    prison.confirmDeals = !prison.confirmDeals;
                    s.registry.save(prison);
                    openDeals(viewer, prison);
                }));
        entries.add(back(49, () -> openMain(viewer, prison)));
        open(viewer, s.text("prison.menu.deals"), 6, entries);
    }

    private void openDeal(TargetId viewer, Prison prison, Deal deal) {
        List<Menus.Entry> entries = new ArrayList<>();
        entries.add(entry(11, "minecraft:lime_wool", s.text("prison.deal.accept"), List.of(), () -> {
            s.context.platform().menus().close(viewer);
            ransom.accept(viewer, deal);
        }));
        entries.add(entry(15, "minecraft:red_wool", s.text("prison.deal.reject"), List.of(), () -> {
            s.context.platform().menus().close(viewer);
            ransom.reject(viewer, deal);
        }));
        entries.add(back(22, () -> openDeals(viewer, prison)));
        open(viewer, s.text("prison.deal.entry", s.name(deal.captive), s.name(deal.buyer)), 3, entries);
    }

    // ---------- настройки ----------

    void openSettings(TargetId viewer, Prison prison) {
        List<Menus.Entry> entries = new ArrayList<>();
        entries.add(entry(4, "minecraft:lodestone", prison.name, List.of(
                s.text("prison.settings.chests", prison.chests.size()),
                s.text("prison.settings.consoles", prison.consoles.size())), null));
        entries.add(entry(10, "minecraft:name_tag", s.text("prison.settings.rename"), List.of(), () ->
                prompts.ask(viewer, "prison.prompt.prison_name", name -> {
                    prison.name = name;
                    s.registry.save(prison);
                    s.message(viewer, "prison.settings.renamed", name);
                })));
        entries.add(entry(12, prison.confirmDeals ? "minecraft:lime_dye" : "minecraft:gray_dye",
                s.text("prison.deal.confirm") + ": " + s.text(prison.confirmDeals ? "prison.on" : "prison.off"),
                List.of(s.text("prison.deal.confirm_lore")), () -> {
                    prison.confirmDeals = !prison.confirmDeals;
                    s.registry.save(prison);
                    openSettings(viewer, prison);
                }));
        if (s.registry.has(prison, viewer, PrisonRight.DELETE)) {
            boolean armed = deleteArmed.contains(viewer);
            entries.add(entry(16, "minecraft:tnt", s.text("prison.settings.delete"),
                    List.of(s.text(armed ? "prison.settings.delete_confirm" : "prison.settings.delete_lore")), () -> {
                        if (deleteArmed.remove(viewer)) {
                            s.context.platform().menus().close(viewer);
                            delete(prison);
                            s.message(viewer, "prison.settings.deleted", prison.name);
                        } else {
                            deleteArmed.add(viewer);
                            openSettings(viewer, prison);
                        }
                    }));
        }
        entries.add(back(18, () -> {
            deleteArmed.remove(viewer);
            openMain(viewer, prison);
        }));
        open(viewer, s.text("prison.menu.settings"), 3, entries);
    }

    /** Пленные тюрьмы остаются в плену по правилам клеток, залоги возвращаются покупателям. */
    void delete(Prison prison) {
        s.registry.inmates.entrySet().stream().filter(entry -> entry.getValue().prisonId.equals(prison.id))
                .map(Map.Entry::getKey).toList().forEach(s.registry::removeInmate);
        for (Deal deal : List.copyOf(s.registry.deals.values())) {
            if (deal.prisonId.equals(prison.id)) {
                deal.status = Deal.Status.REJECTED;
                s.registry.saveDeal(deal);
            }
        }
        s.registry.delete(prison);
    }

    // ---------- общее ----------

    /** Меню-хранилище для образцов: что положили, становится списком, а сами вещи возвращаются. */
    private void samples(TargetId viewer, String title, Consumer<Map<String, Integer>> apply) {
        s.context.platform().menus().openStorage(viewer, new Menus.StorageMenu(s.text("prison.samples.title", title), 1,
                List.of(), stacks -> {
                    s.giveBack(viewer, stacks);
                    if (!stacks.isEmpty()) {
                        apply.accept(s.samples(stacks));
                    }
                }));
    }

    private Menus.Entry back(int slot, Runnable action) {
        return entry(slot, "minecraft:arrow", s.text("prison.menu.back"), List.of(), action);
    }

    private static Menus.Entry entry(int slot, String icon, String label, List<String> lore, Runnable action) {
        return new Menus.Entry(slot, icon, label, lore, action);
    }

    private void open(TargetId viewer, String title, int rows, List<Menus.Entry> entries) {
        s.context.platform().menus().open(viewer, new Menus.Menu(title, rows, entries));
    }

    /** Цена и нормы для тестов: стопки как образцы. */
    static List<ItemStackData> stacks(Map<String, Integer> counts) {
        List<ItemStackData> stacks = new ArrayList<>();
        counts.forEach((id, count) -> stacks.add(ItemStackData.of(id, count)));
        return stacks;
    }
}
