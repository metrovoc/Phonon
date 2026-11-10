package com.metrovoc.phonon.server;

import java.util.Locale;

/**
 * Parsed arguments of {@code /phonon add}: an optional name followed by a link.
 * The link has no spaces, so the name needs no quotes.
 */
public record AddRequest(String name, String url) {

    /** Returns null when the text does not end in an http(s) link. */
    public static AddRequest parse(String text) {
        String trimmed = text.trim();
        int split = lastWhitespace(trimmed);
        String url = trimmed.substring(split + 1);
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return null;
        }
        return new AddRequest(unquote(trimmed.substring(0, split + 1).trim()), url);
    }

    public boolean hasName() {
        return !name.isEmpty();
    }

    private static int lastWhitespace(String text) {
        for (int i = text.length() - 1; i >= 0; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static String unquote(String name) {
        boolean quoted = name.length() >= 2 && name.startsWith("\"") && name.endsWith("\"");
        return quoted ? name.substring(1, name.length() - 1).trim() : name;
    }
}
