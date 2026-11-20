package com.torturemod.feature.help;

import com.torturemod.api.feature.FeatureContext;
import com.torturemod.api.model.Book;

import java.util.ArrayList;
import java.util.List;

/**
 * Книга-справка из языкового файла: оглавление со ссылками и главы. Страницы главы идут ключами
 * help.chapter.&lt;глава&gt;.1, .2 и так далее, пока ключ есть в файле.
 */
final class GuideBook {
    static final List<String> CHAPTERS = List.of("capture", "torture", "escape", "convoy", "cuffs", "prison",
            "zones", "roles", "quota", "ransom", "minions", "admin");
    private static final int MAX_PAGES_PER_CHAPTER = 20;

    private final FeatureContext context;

    GuideBook(FeatureContext context) {
        this.context = context;
    }

    Book build() {
        List<List<String>> chapters = new ArrayList<>();
        for (String chapter : CHAPTERS) {
            List<String> pages = new ArrayList<>();
            for (int i = 1; i <= MAX_PAGES_PER_CHAPTER; i++) {
                String key = "help.chapter." + chapter + "." + i;
                String text = context.messages().text(key);
                if (text.equals(key)) {
                    break;
                }
                pages.add(text);
            }
            chapters.add(pages);
        }
        // Оглавление занимает первые страницы: по 12 строк на страницу.
        int tocPages = Math.max(1, (CHAPTERS.size() + 11) / 12);
        List<Book.Page> pages = new ArrayList<>();
        int next = tocPages + 1;
        List<Book.Span> toc = new ArrayList<>();
        toc.add(new Book.Span(context.messages().text("help.book.contents") + "\n", true, 0));
        for (int c = 0; c < CHAPTERS.size(); c++) {
            if (chapters.get(c).isEmpty()) {
                continue;
            }
            toc.add(new Book.Span("› " + title(CHAPTERS.get(c)) + "\n", false, next));
            next += chapters.get(c).size();
            if (toc.size() == 13) {
                pages.add(new Book.Page(List.copyOf(toc)));
                toc.clear();
            }
        }
        if (!toc.isEmpty()) {
            pages.add(new Book.Page(List.copyOf(toc)));
        }
        while (pages.size() < tocPages) {
            pages.add(new Book.Page(List.of(Book.Span.plain(""))));
        }
        for (int c = 0; c < CHAPTERS.size(); c++) {
            List<String> chapter = chapters.get(c);
            for (int p = 0; p < chapter.size(); p++) {
                List<Book.Span> spans = new ArrayList<>();
                if (p == 0) {
                    spans.add(new Book.Span(title(CHAPTERS.get(c)) + "\n\n", true, 0));
                }
                spans.add(Book.Span.plain(chapter.get(p)));
                spans.add(new Book.Span("\n\n" + context.messages().text("help.book.back"), false, 1));
                pages.add(new Book.Page(spans));
            }
        }
        return new Book(context.messages().text("help.book.title"), "Torture Mod", pages);
    }

    private String title(String chapter) {
        return context.messages().text("help.chapter." + chapter + ".title");
    }
}
