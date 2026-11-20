package com.torturemod.feature.prison;

import com.torturemod.api.captivity.CaptivityService;
import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.model.Area;
import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.model.TargetState;
import com.torturemod.api.prison.ChestRole;
import com.torturemod.api.prison.ConsoleSection;
import com.torturemod.api.prison.OwnershipSource;
import com.torturemod.api.prison.PrisonInfo;
import com.torturemod.api.prison.PrisonRight;
import com.torturemod.api.prison.PrisonService;
import com.torturemod.api.util.Json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Тюрьмы, пленные в них и сделки выкупа. Живут в памяти основного потока, запись в базу в фоне. */
final class PrisonRegistry implements PrisonService {
    static final String PRISONS = "prisons";
    static final String INMATES = "prison_inmates";
    static final String DEALS = "prison_deals";

    private final FeatureContext context;
    private final Supplier<PrisonSettings> settings;
    final Map<String, Prison> prisons = new LinkedHashMap<>();
    final Map<TargetId, Inmate> inmates = new LinkedHashMap<>();
    final Map<String, Deal> deals = new LinkedHashMap<>();
    private final List<OwnershipSource> sources = new ArrayList<>();
    final List<ConsoleSection> sections = new ArrayList<>();

    PrisonRegistry(FeatureContext context, Supplier<PrisonSettings> settings) {
        this.context = context;
        this.settings = settings;
    }

    /** Однократная загрузка при запуске. Условия тех, кто уже не в плену, удаляются. */
    void load(CaptivityService captivity) {
        context.storage().loadDocuments(PRISONS).join()
                .forEach((key, json) -> read(key, () -> prisons.put(key, Codec.prison(Json.readObject(json)))));
        context.storage().loadDocuments(INMATES).join().forEach((key, json) -> read(key, () -> {
            TargetId captive = new TargetId(UUID.fromString(key));
            Inmate inmate = Codec.inmate(Json.readObject(json));
            if (captivity.isCaptive(captive) && prisons.containsKey(inmate.prisonId)) {
                inmates.put(captive, inmate);
            } else {
                context.storage().deleteDocument(INMATES, key);
            }
        }));
        context.storage().loadDocuments(DEALS).join()
                .forEach((key, json) -> read(key, () -> deals.put(key, Codec.deal(Json.readObject(json)))));
    }

    private void read(String key, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            context.platform().log().error("Cannot read prison document " + key, e);
        }
    }

    Prison create(String name, TargetId owner, Area area) {
        Prison prison = new Prison(UUID.randomUUID().toString(), name, owner, area);
        prisons.put(prison.id, prison);
        save(prison);
        return prison;
    }

    void save(Prison prison) {
        context.storage().saveDocument(PRISONS, prison.id, Json.write(Codec.prison(prison))).exceptionally(this::fail);
    }

    void delete(Prison prison) {
        prisons.remove(prison.id);
        context.storage().deleteDocument(PRISONS, prison.id).exceptionally(this::fail);
    }

    void saveInmate(TargetId captive) {
        Inmate inmate = inmates.get(captive);
        if (inmate != null) {
            context.storage().saveDocument(INMATES, captive.value().toString(), Json.write(Codec.inmate(inmate)))
                    .exceptionally(this::fail);
        }
    }

    void removeInmate(TargetId captive) {
        if (inmates.remove(captive) != null) {
            context.storage().deleteDocument(INMATES, captive.value().toString()).exceptionally(this::fail);
        }
    }

    void saveDeal(Deal deal) {
        deals.put(deal.id, deal);
        context.storage().saveDocument(DEALS, deal.id, Json.write(Codec.deal(deal))).exceptionally(this::fail);
    }

    void removeDeal(Deal deal) {
        deals.remove(deal.id);
        context.storage().deleteDocument(DEALS, deal.id).exceptionally(this::fail);
    }

    private Void fail(Throwable error) {
        context.platform().log().error("Prison storage failed", error);
        return null;
    }

    Optional<Prison> at(Location location) {
        return prisons.values().stream().filter(prison -> prison.area.contains(location)).findFirst();
    }

    Optional<Prison> at(BlockLocation block) {
        return prisons.values().stream().filter(prison -> prison.area.contains(block)).findFirst();
    }

    Optional<Prison> byId(String id) {
        return Optional.ofNullable(prisons.get(id));
    }

    Optional<Prison> ofInmate(TargetId captive) {
        return Optional.ofNullable(inmates.get(captive)).flatMap(inmate -> byId(inmate.prisonId));
    }

    long ownedBy(TargetId owner) {
        return prisons.values().stream().filter(prison -> prison.owner.equals(owner)).count();
    }

    /** Роль игрока: владелец, участник или роль из привата по таблице из конфига. */
    Optional<Role> roleOf(Prison prison, TargetId player) {
        if (prison.owner.equals(player)) {
            return prison.role(Role.OWNER);
        }
        String roleId = prison.members.get(player);
        if (roleId != null) {
            return prison.role(roleId);
        }
        for (OwnershipSource source : sources) {
            Optional<String> claim = source.claimRole(player, prison.area);
            if (claim.isPresent()) {
                String mapped = settings.get().ownershipRoles().get(claim.get());
                if (mapped != null) {
                    return prison.role(mapped);
                }
            }
        }
        return Optional.empty();
    }

    boolean has(Prison prison, TargetId player, PrisonRight right) {
        return roleOf(prison, player).filter(role -> role.rights.contains(right)).isPresent();
    }

    boolean isTeam(Prison prison, TargetId player) {
        return roleOf(prison, player).isPresent();
    }

    boolean isFriend(Prison prison, TargetId player) {
        if (isTeam(prison, player)) {
            return true;
        }
        Optional<String> ownerTeam = context.platform().players().team(prison.owner);
        return ownerTeam.isPresent() && ownerTeam.equals(context.platform().players().team(player));
    }

    /** Может ли игрок зарегистрировать тюрьму здесь: решает приват, если он есть. */
    boolean mayRegister(TargetId player, Area area) {
        for (OwnershipSource source : sources) {
            Optional<Boolean> decision = source.mayRegister(player, area);
            if (decision.isPresent()) {
                return decision.get();
            }
        }
        return true;
    }

    // ---------- PrisonService ----------

    @Override
    public Collection<PrisonInfo> prisons() {
        return prisons.values().stream().map(Prison::info).toList();
    }

    @Override
    public Optional<PrisonInfo> prison(String prisonId) {
        return byId(prisonId).map(Prison::info);
    }

    @Override
    public Optional<PrisonInfo> prisonAt(Location location) {
        return at(location).map(Prison::info);
    }

    @Override
    public Optional<String> prisonOf(TargetId captive) {
        return Optional.ofNullable(inmates.get(captive)).map(inmate -> inmate.prisonId);
    }

    @Override
    public boolean hasRight(String prisonId, TargetId player, PrisonRight right) {
        return byId(prisonId).filter(prison -> has(prison, player, right)).isPresent();
    }

    @Override
    public boolean isFriend(String prisonId, TargetId player) {
        return byId(prisonId).filter(prison -> isFriend(prison, player)).isPresent();
    }

    @Override
    public List<TargetId> onlineGuards(String prisonId) {
        Optional<Prison> prison = byId(prisonId);
        if (prison.isEmpty()) {
            return List.of();
        }
        return context.platform().players().online().stream().map(TargetState::id)
                .filter(player -> isFriend(prison.get(), player)).toList();
    }

    @Override
    public List<BlockLocation> chests(String prisonId, ChestRole role) {
        return byId(prisonId).map(prison -> prison.chests(role)).orElse(List.of());
    }

    @Override
    public void addOwnershipSource(OwnershipSource source) {
        sources.add(source);
    }

    @Override
    public void addConsoleSection(ConsoleSection section) {
        sections.add(section);
    }
}
