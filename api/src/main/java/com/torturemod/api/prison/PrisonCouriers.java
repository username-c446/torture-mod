package com.torturemod.api.prison;

import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.ItemStackData;
import com.torturemod.api.model.TargetId;

import java.util.List;

/** Мобы-курьеры тюрьмы. Предоставляет фича слуг. */
public interface PrisonCouriers {
    /** @return false, если свободного курьера нет: тогда награду относит команда */
    boolean deliver(String prisonId, TargetId captive, BlockLocation fromChest, List<ItemStackData> items);
}
