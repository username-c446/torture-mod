package com.torturemod.api.model;

import java.util.List;

/** Ванильная книга, которую сервер показывает игроку без выдачи предмета. */
public record Book(String title, String author, List<Page> pages) {
    public record Page(List<Span> spans) {
    }

    /** @param goToPage номер страницы с единицы, на которую ведёт нажатие; 0, если это не ссылка */
    public record Span(String text, boolean bold, int goToPage) {
        public static Span plain(String text) {
            return new Span(text, false, 0);
        }
    }
}
