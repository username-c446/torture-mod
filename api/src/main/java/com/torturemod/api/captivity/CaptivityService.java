package com.torturemod.api.captivity;

import com.torturemod.api.model.BlockLocation;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;

import java.util.Collection;
import java.util.Optional;

/** Плен для других фич и интеграций. Вызывается на основном потоке сервера. */
public interface CaptivityService {
    Optional<Captive> find(TargetId target);

    boolean isCaptive(TargetId target);

    Collection<Captive> captives();

    /** Плен без клетки, как по команде администратора. Пусто при успехе. */
    Optional<CaptureFailure> captureWithoutCell(TargetId target, Optional<TargetId> captor);

    /** Сохраняет сопротивление пленного. Вызывает фича сопротивления. */
    void updateResistance(TargetId target, double resistance, boolean broken);

    /** Пока пленного ведут или везут, клетка не проверяется: выход из неё и её поломка не освобождают. */
    void setCellChecksSuspended(TargetId target, boolean suspended);

    /** Если пленный стоит в замкнутой клетке с маркером, она становится его клеткой. */
    boolean moveToCell(TargetId target);

    boolean isCellChecksSuspended(TargetId target);

    /** Правило о действиях пленного руками; правила спрашиваются от меньшего приоритета. */
    void addActionRule(int priority, ActionRule rule);

    /** Можно ли пленному это действие: решают правила, без них всё запрещено. */
    boolean mayAct(TargetId captive, CaptiveAction action, BlockLocation where);

    void addContainment(Containment containment);

    void addAccessRule(AccessRule rule);

    /** Можно ли игроку это сделать с пленным или целью; без правил можно всем. */
    boolean allows(TargetId actor, AccessRight right, Optional<TargetId> captive, Location where);

    /** @return false, если цель не была в плену */
    boolean release(TargetId target, ReleaseReason reason);
}
