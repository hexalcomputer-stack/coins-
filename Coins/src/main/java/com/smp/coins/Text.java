package com.smp.coins;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Locale;

/** Small helpers for colored text and number formatting. */
public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private Text() {
    }

    /** Converts &-color codes (and &#RRGGBB) into a Component. */
    public static Component color(String s) {
        return LEGACY.deserialize(s == null ? "" : s);
    }

    /** Same as {@link #color(String)} but with italics turned off (for item names and lore). */
    public static Component item(String s) {
        return color(s).decoration(TextDecoration.ITALIC, false);
    }

    /** 12500 -> "12,500" */
    public static String number(long n) {
        return String.format(Locale.US, "%,d", n);
    }

    /** MAGMA_CUBE -> "Magma Cube" */
    public static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
