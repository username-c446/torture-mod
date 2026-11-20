package com.torturemod.feature.prison;

import com.torturemod.api.model.Area;
import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.ItemStackData;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.prison.ChestRole;
import com.torturemod.api.prison.PrisonRight;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Перевод тюрем, условий пленных и сделок в документы JSON и обратно. */
final class Codec {
    private Codec() {
    }

    static Map<String, Object> prison(Prison prison) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", prison.id);
        out.put("name", prison.name);
        out.put("owner", prison.owner.value().toString());
        out.put("area", area(prison.area));
        Map<String, Object> members = new LinkedHashMap<>();
        prison.members.forEach((player, role) -> members.put(player.value().toString(), role));
        out.put("members", members);
        List<Object> roles = new ArrayList<>();
        for (Role role : prison.customRoles.values()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", role.id);
            map.put("name", role.name);
            map.put("rights", role.rights.stream().map(Enum::name).toList());
            roles.add(map);
        }
        out.put("roles", roles);
        List<Object> zones = new ArrayList<>();
        for (Zone zone : prison.zones) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", zone.name);
            map.put("area", area(zone.area));
            map.put("priority", zone.priority);
            map.put("flags", zone.flags.stream().map(Enum::name).toList());
            map.put("quota", new LinkedHashMap<>(zone.quota));
            zones.add(map);
        }
        out.put("zones", zones);
        out.put("baseFlags", prison.baseFlags.stream().map(Enum::name).toList());
        out.put("quota", new LinkedHashMap<>(prison.quota));
        Map<String, Object> chests = new LinkedHashMap<>();
        prison.chests.forEach((block, role) -> chests.put(block(block), role.name()));
        out.put("chests", chests);
        out.put("consoles", prison.consoles.stream().map(Codec::block).toList());
        out.put("confirmDeals", prison.confirmDeals);
        out.put("rewardSentencePercent", prison.rewardSentencePercent);
        out.put("rewardRecoveryMultiplier", prison.rewardRecoveryMultiplier);
        out.put("rewardItems", new LinkedHashMap<>(prison.rewardItems));
        return out;
    }

    static Prison prison(Map<String, Object> in) {
        Prison prison = new Prison(str(in.get("id")), str(in.get("name")), id(in.get("owner")), area(in.get("area")));
        map(in.get("members")).forEach((player, role) -> prison.members.put(id(player), str(role)));
        for (Object item : list(in.get("roles"))) {
            Map<String, Object> map = map(item);
            Role role = new Role(str(map.get("id")), str(map.get("name")), rights(map.get("rights")), false);
            prison.customRoles.put(role.id, role);
        }
        for (Object item : list(in.get("zones"))) {
            Map<String, Object> map = map(item);
            Zone zone = new Zone(str(map.get("name")), area(map.get("area")), (int) num(map.get("priority")),
                    flags(map.get("flags")));
            counts(map.get("quota"), zone.quota);
            prison.zones.add(zone);
        }
        prison.baseFlags.clear();
        prison.baseFlags.addAll(flags(in.get("baseFlags")));
        counts(in.get("quota"), prison.quota);
        map(in.get("chests")).forEach((block, role) -> prison.chests.put(block(block), ChestRole.valueOf(str(role))));
        list(in.get("consoles")).forEach(block -> prison.consoles.add(block(str(block))));
        prison.confirmDeals = Boolean.TRUE.equals(in.get("confirmDeals"));
        prison.rewardSentencePercent = (int) num(in.get("rewardSentencePercent"));
        prison.rewardRecoveryMultiplier = in.get("rewardRecoveryMultiplier") == null ? 1
                : num(in.get("rewardRecoveryMultiplier"));
        counts(in.get("rewardItems"), prison.rewardItems);
        return prison;
    }

    static Map<String, Object> inmate(Inmate inmate) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("prison", inmate.prisonId);
        out.put("sentenceLeft", inmate.sentenceLeftMillis);
        out.put("quota", new LinkedHashMap<>(inmate.quotaOverride));
        out.put("progress", new LinkedHashMap<>(inmate.progress));
        out.put("dayElapsed", inmate.dayElapsedMillis);
        out.put("completedToday", inmate.completedToday);
        out.put("price", new LinkedHashMap<>(inmate.price));
        return out;
    }

    static Inmate inmate(Map<String, Object> in) {
        Inmate inmate = new Inmate(str(in.get("prison")));
        inmate.sentenceLeftMillis = in.get("sentenceLeft") == null ? -1 : (long) num(in.get("sentenceLeft"));
        counts(in.get("quota"), inmate.quotaOverride);
        counts(in.get("progress"), inmate.progress);
        inmate.dayElapsedMillis = (long) num(in.get("dayElapsed"));
        inmate.completedToday = Boolean.TRUE.equals(in.get("completedToday"));
        counts(in.get("price"), inmate.price);
        return inmate;
    }

    static Map<String, Object> deal(Deal deal) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", deal.id);
        out.put("prison", deal.prisonId);
        out.put("captive", deal.captive.value().toString());
        out.put("buyer", deal.buyer.value().toString());
        out.put("escrow", stacks(deal.escrow));
        out.put("createdAt", deal.createdAt);
        out.put("status", deal.status.name());
        return out;
    }

    static Deal deal(Map<String, Object> in) {
        return new Deal(str(in.get("id")), str(in.get("prison")), id(in.get("captive")), id(in.get("buyer")),
                stacks(in.get("escrow")), (long) num(in.get("createdAt")), Deal.Status.valueOf(str(in.get("status"))));
    }

    static List<Object> stacks(List<ItemStackData> stacks) {
        List<Object> out = new ArrayList<>();
        for (ItemStackData stack : stacks) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", stack.itemId());
            map.put("count", stack.count());
            map.put("data", stack.data());
            out.add(map);
        }
        return out;
    }

    static List<ItemStackData> stacks(Object in) {
        List<ItemStackData> out = new ArrayList<>();
        for (Object item : list(in)) {
            Map<String, Object> map = map(item);
            out.add(new ItemStackData(str(map.get("id")), (int) num(map.get("count")), str(map.get("data"))));
        }
        return out;
    }

    static List<Object> area(Area area) {
        return List.of(area.worldId(), area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ());
    }

    static Area area(Object in) {
        List<Object> list = list(in);
        return new Area(str(list.get(0)), (int) num(list.get(1)), (int) num(list.get(2)), (int) num(list.get(3)),
                (int) num(list.get(4)), (int) num(list.get(5)), (int) num(list.get(6)));
    }

    static String block(BlockLocation block) {
        return block.worldId() + "|" + block.x() + "|" + block.y() + "|" + block.z();
    }

    static BlockLocation block(String in) {
        String[] parts = in.split("\\|");
        return new BlockLocation(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3]));
    }

    private static Set<Flag> flags(Object in) {
        Set<Flag> flags = EnumSet.noneOf(Flag.class);
        for (Object item : list(in)) {
            try {
                flags.add(Flag.valueOf(str(item)));
            } catch (IllegalArgumentException ignored) {
                // Флаг из более новой версии мода пропускается.
            }
        }
        return flags;
    }

    private static Set<PrisonRight> rights(Object in) {
        Set<PrisonRight> rights = EnumSet.noneOf(PrisonRight.class);
        for (Object item : list(in)) {
            try {
                rights.add(PrisonRight.valueOf(str(item)));
            } catch (IllegalArgumentException ignored) {
                // Право из более новой версии мода пропускается.
            }
        }
        return rights;
    }

    private static void counts(Object in, Map<String, Integer> into) {
        map(in).forEach((key, value) -> into.put(key, (int) num(value)));
    }

    private static TargetId id(Object in) {
        return new TargetId(UUID.fromString(str(in)));
    }

    private static String str(Object in) {
        return in == null ? "" : in.toString();
    }

    private static double num(Object in) {
        return in instanceof Number number ? number.doubleValue() : 0;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object in) {
        return in instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object in) {
        return in instanceof List<?> list ? (List<Object>) list : List.of();
    }
}
