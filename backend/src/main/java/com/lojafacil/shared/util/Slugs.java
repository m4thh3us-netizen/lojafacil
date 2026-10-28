package com.lojafacil.shared.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class Slugs {
    public static final Pattern VALID = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{1,58}[a-z0-9])$");

    public static final Set<String> RESERVED = Set.of(
            "admin", "api", "login", "cadastro", "painel", "loja", "files", "swagger", "docs",
            "recuperar-senha", "redefinir-senha", "convite", "assets", "static");

    private Slugs() {
    }

    public static String slugify(String text) {
        String noAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = noAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.length() > 80 ? slug.substring(0, 80).replaceAll("-$", "") : slug;
    }

    public static boolean isValidStoreSlug(String slug) {
        return slug != null && VALID.matcher(slug).matches() && !RESERVED.contains(slug);
    }
}
