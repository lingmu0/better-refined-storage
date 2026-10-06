import net.xuwu.bettersophisticatedstorage.client.SidebarText;

import java.util.function.ToIntFunction;
import java.util.Locale;

/** Pure Java regression; no Minecraft classes, client, server or graphics context are needed. */
public final class SidebarTextRegression
{
    public static void main(String[] args)
    {
        check(SidebarText.amountLabel(1L) == null, "A single item must not display a count override");
        check("2".equals(SidebarText.amountLabel(2L)), "Two items must show their count");
        check("64".equals(SidebarText.amountLabel(64L)), "A full stack must show its count");
        check("999".equals(SidebarText.amountLabel(999L)), "Counts below one thousand remain exact");
        check("1.0K".equals(SidebarText.amountLabel(1_000L)), "Thousands retain K notation");
        check("1.0M".equals(SidebarText.amountLabel(1_000_000L)), "Millions retain M notation");
        check("1.0B".equals(SidebarText.amountLabel(1_000_000_000L)), "Billions retain B notation");
        Locale originalLocale = Locale.getDefault();
        try
        {
            Locale.setDefault(Locale.GERMANY);
            check("1.5K".equals(SidebarText.amountLabel(1_500L)), "Count labels are stable across locales");
        }
        finally
        {
            Locale.setDefault(originalLocale);
        }
        ToIntFunction<String> metrics = value -> value.codePoints().map(c -> c == '.' ? 2 : c > 127 ? 9 : 6).sum();
        check(SidebarText.ellipsize("Search...", 42, metrics).equals("Search..."), "Exact fit remains unchanged");
        check(SidebarText.ellipsize("Search Sophisticated Storage", 72, metrics).equals("Search Soph..."),
                "Long English prompt must end with three dots");
        check(SidebarText.ellipsize("搜索精致存储网络", 60, metrics).equals("搜索精致存储..."),
                "Chinese prompt must fit by measured width, not character count");
        check(SidebarText.ellipsize("Search", 0, metrics).isEmpty(), "Zero width");
        check(SidebarText.ellipsize("Search", -1, metrics).isEmpty(), "Negative width");
        check(SidebarText.ellipsize("Search", 4, metrics).equals(".."), "Very narrow field remains bounded");
        check(SidebarText.ellipsize("", 10, metrics).isEmpty(), "Empty text");
        String unicode = "A\uD83D\uDE00B long prompt";
        for (int width = 0; width < 200; width++)
        {
            String fitted = SidebarText.ellipsize(unicode, width, metrics);
            check(metrics.applyAsInt(fitted) <= width, "Every output must fit its available width");
            for (int index = 0; index < fitted.length(); index++)
            {
                char character = fitted.charAt(index);
                if (Character.isHighSurrogate(character))
                {
                    check(index + 1 < fitted.length() && Character.isLowSurrogate(fitted.charAt(++index)),
                            "Truncation must preserve supplementary Unicode characters");
                }
                else
                {
                    check(!Character.isLowSurrogate(character), "No orphan surrogate");
                }
            }
        }
        System.out.println("Sidebar text regression passed: single-item counts, K/M/B, locales, English, Chinese and Unicode.");
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
        {
            throw new AssertionError(message);
        }
    }
}
