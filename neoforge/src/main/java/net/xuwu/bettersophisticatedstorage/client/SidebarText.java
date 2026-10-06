package net.xuwu.bettersophisticatedstorage.client;

import java.util.Locale;
import java.util.function.ToIntFunction;

/** Width-aware text fitting with the active font's metrics and Unicode-safe truncation. */
public final class SidebarText
{
    private SidebarText()
    {
    }

    /** Null lets vanilla omit the count for a normalized single-item display stack. */
    public static String amountLabel(long amount)
    {
        if (amount == 1L)
        {
            return null;
        }
        if (amount >= 1_000_000_000L)
        {
            return String.format(Locale.ROOT, "%.1fB", amount / 1_000_000_000.0D);
        }
        if (amount >= 1_000_000L)
        {
            return String.format(Locale.ROOT, "%.1fM", amount / 1_000_000.0D);
        }
        if (amount >= 1_000L)
        {
            return String.format(Locale.ROOT, "%.1fK", amount / 1_000.0D);
        }
        return Long.toString(amount);
    }

    public static String ellipsize(String text, int maxWidth, ToIntFunction<String> width)
    {
        if (maxWidth <= 0)
        {
            return "";
        }
        if (width.applyAsInt(text) <= maxWidth)
        {
            return text;
        }
        String suffix = "...";
        while (width.applyAsInt(suffix) > maxWidth)
        {
            suffix = suffix.substring(0, suffix.length() - 1);
        }
        int end = text.length();
        while (end > 0 && width.applyAsInt(text.substring(0, end) + suffix) > maxWidth)
        {
            end = text.offsetByCodePoints(end, -1);
        }
        return text.substring(0, end) + suffix;
    }
}
