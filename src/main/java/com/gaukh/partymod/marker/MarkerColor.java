package com.gaukh.partymod.marker;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Enum representing available marker colors.
 * Each color maps to a PNG icon file.
 */
public enum MarkerColor {
    RED("marker_red.png"),
    GREEN("marker_green.png"),
    BLUE("marker_blue.png"),
    YELLOW("marker_yellow.png"),
    PURPLE("marker_purple.png"),
    WHITE("marker_white.png"),
    PARTY("marker_party.png"); // Special icon for party members

    private final String imagePath;

    MarkerColor(@Nonnull String imagePath) {
        this.imagePath = imagePath;
    }

    @Nonnull
    public String getImagePath() {
        return imagePath;
    }

    /**
     * Gets a marker color by name (case-insensitive).
     *
     * @param name The color name
     * @return The color, or null if not found
     */
    @Nullable
    public static MarkerColor fromName(@Nonnull String name) {
        for (MarkerColor color : values()) {
            if (color.name().equalsIgnoreCase(name)) {
                return color;
            }
        }
        return null;
    }

    /**
     * Gets a marker color by name, or returns the default color.
     *
     * @param name The color name
     * @param defaultColor The default color if not found
     * @return The color
     */
    @Nonnull
    public static MarkerColor fromNameOrDefault(@Nullable String name, @Nonnull MarkerColor defaultColor) {
        if (name == null || name.isEmpty()) {
            return defaultColor;
        }
        MarkerColor color = fromName(name);
        return color != null ? color : defaultColor;
    }
}
