package com.torturemod.api.items;

import java.util.List;

/**
 * Предмет мода без новых предметов в игре: ванильный предмет с меткой мода, своим названием
 * и номером модели (CustomModelData) для ресурспака. В ядре он виден под своим ID, например
 * torturemod:handcuffs.
 */
public record CustomItem(String id, String baseItemId, int customModelData, String name, List<String> lore) {
}
