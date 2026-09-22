package dev.lohrel.plasticmemories.provider;

import java.util.Optional;

/**
 * Optional sampling settings a player can tune. Each is sent under its OpenAI-compatible name only
 * when set, because strict APIs (OpenAI itself) reject names they don't know, like top_k.
 */
public enum SamplingParameter {
    TEMPERATURE("temperature", "Temperature", 0, 2, false),
    TOP_P("top_p", "Top P", 0, 1, false),
    TOP_K("top_k", "Top K", 0, 1_000, true),
    MIN_P("min_p", "Min P", 0, 1, false),
    FREQUENCY_PENALTY("frequency_penalty", "Frequency penalty", -2, 2, false),
    PRESENCE_PENALTY("presence_penalty", "Presence penalty", -2, 2, false),
    REPETITION_PENALTY("repetition_penalty", "Repetition penalty", 0, 3, false),
    MAX_TOKENS("max_tokens", "Max reply tokens", 1, 8_192, true);

    private final String wireName;
    private final String label;
    private final double minimum;
    private final double maximum;
    private final boolean integer;

    SamplingParameter(String wireName, String label, double minimum, double maximum, boolean integer) {
        this.wireName = wireName;
        this.label = label;
        this.minimum = minimum;
        this.maximum = maximum;
        this.integer = integer;
    }

    /** Name in the request JSON and in provider.json. */
    public String wireName() {
        return wireName;
    }

    public String label() {
        return label;
    }

    public boolean integer() {
        return integer;
    }

    /** Hint for the settings screen, e.g. "0 – 2". */
    public String rangeHint() {
        return format(minimum) + " – " + format(maximum);
    }

    /** Blank means unset. Anything else must be a number in range. */
    public Optional<Double> parse(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(validate(Double.parseDouble(trimmed)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a number.");
        }
    }

    public double validate(double value) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(label + " must be between " + rangeHint() + ".");
        }
        if (integer && value != Math.rint(value)) {
            throw new IllegalArgumentException(label + " must be a whole number.");
        }
        return value;
    }

    public String format(double value) {
        return integer || value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    public static Optional<SamplingParameter> fromWireName(String name) {
        for (SamplingParameter parameter : values()) {
            if (parameter.wireName.equals(name)) {
                return Optional.of(parameter);
            }
        }
        return Optional.empty();
    }
}
