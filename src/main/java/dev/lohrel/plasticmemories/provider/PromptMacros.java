package dev.lohrel.plasticmemories.provider;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SillyTavern-style name macros in card, lorebook and profile text: {{user}} / <USER> become the
 * player's persona name and {{char}} / <BOT> the NPC's name. Other macros are left as they are.
 */
public final class PromptMacros {
    // One pattern, one pass: a name that itself contains "{{char}}" or "<BOT>" isn't expanded again.
    private static final Pattern MACROS = Pattern.compile("\\{\\{(user|char)}}|<(USER|BOT)>", Pattern.CASE_INSENSITIVE);

    private PromptMacros() {
    }

    public static String apply(String text, String userName, String charName) {
        return MACROS.matcher(text).replaceAll(match -> {
            String name = (match.group(1) != null ? match.group(1) : match.group(2)).toLowerCase(Locale.ROOT);
            return Matcher.quoteReplacement(name.equals("user") ? userName : charName);
        });
    }
}
