package com.torturemod.api.port;

import com.torturemod.api.model.Book;
import com.torturemod.api.model.Location;
import com.torturemod.api.model.TargetId;

public interface Feedback {
    void message(TargetId receiver, String text);

    void actionBar(TargetId receiver, String text);

    void sound(Location location, String soundId);

    void particles(Location location, String particleId, int count);

    /** Одна частица, которую видит только этот игрок: например, граница размечаемой области. */
    void particleFor(TargetId viewer, Location location, String particleId);

    /** Открывает книгу, не кладя её в инвентарь. */
    void openBook(TargetId viewer, Book book);

    /** Показывает или обновляет боссбар. Ключ различает полосы, прогресс от 0 до 1. */
    void showBar(TargetId viewer, String key, String title, float progress);

    void hideBar(TargetId viewer, String key);
}
