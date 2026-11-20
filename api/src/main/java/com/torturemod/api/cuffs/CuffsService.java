package com.torturemod.api.cuffs;

import com.torturemod.api.model.TargetId;

/** Наручники пленного. Вызывается на основном потоке сервера. */
public interface CuffsService {
    boolean isCuffed(TargetId captive);

    /** Надевает сразу, без процесса надевания. */
    void cuff(TargetId captive);

    /** @return false, если наручников не было */
    boolean uncuff(TargetId captive);
}
