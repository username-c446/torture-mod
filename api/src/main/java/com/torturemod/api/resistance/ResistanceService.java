package com.torturemod.api.resistance;

import com.torturemod.api.model.TargetId;

import java.util.Optional;

/** Шкала сопротивления пленных. Вызывается на основном потоке сервера. */
public interface ResistanceService {
    Optional<ResistanceView> view(TargetId captive);

    /**
     * Ослабляет пленного пыткой: учитывает привыкание и уязвимости.
     *
     * @return на сколько упало сопротивление
     */
    double weaken(TargetId captive, String tortureType, double baseDrop);

    boolean isBroken(TargetId captive);

    /** Наказание вне пыток: снимает долю полной шкалы без учёта привыкания и уязвимостей. */
    void penalize(TargetId captive, double fractionOfMax);

    /** Сразу ломает пленного. */
    void breakNow(TargetId captive);

    /** Восстановление идёт быстрее или медленнее в заданное число раз, пока не истечёт срок. */
    void boostRecovery(TargetId captive, double multiplier, long durationMillis);
}
