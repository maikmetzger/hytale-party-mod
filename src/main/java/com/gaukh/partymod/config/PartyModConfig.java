package com.gaukh.partymod.config;

import javax.annotation.Nonnull;

/**
 * Configuration for the PartyMod plugin.
 * Simple POJO without Codec - will use manual JSON serialization.
 */
public class PartyModConfig {

    // -1 = unlimited
    private int maxPartySize = -1;
    private int inviteTimeoutSeconds = 60;
    private boolean pvpProtectionEnabled = true;
    private String defaultMarkerColor = "Blue";
    private boolean showDistanceOnCompass = true;
    private boolean showHealthBars = true;

    public PartyModConfig() {
        // Default constructor
    }

    // Getters

    public int getMaxPartySize() {
        return maxPartySize;
    }

    public int getInviteTimeoutSeconds() {
        return inviteTimeoutSeconds;
    }

    public boolean isPvpProtectionEnabled() {
        return pvpProtectionEnabled;
    }

    @Nonnull
    public String getDefaultMarkerColor() {
        return defaultMarkerColor;
    }

    public boolean isShowDistanceOnCompass() {
        return showDistanceOnCompass;
    }

    public boolean isShowHealthBars() {
        return showHealthBars;
    }

    // Setters

    public void setMaxPartySize(int maxPartySize) {
        this.maxPartySize = maxPartySize;
    }

    public void setInviteTimeoutSeconds(int inviteTimeoutSeconds) {
        this.inviteTimeoutSeconds = inviteTimeoutSeconds;
    }

    public void setPvpProtectionEnabled(boolean pvpProtectionEnabled) {
        this.pvpProtectionEnabled = pvpProtectionEnabled;
    }

    public void setDefaultMarkerColor(@Nonnull String defaultMarkerColor) {
        this.defaultMarkerColor = defaultMarkerColor;
    }

    public void setShowDistanceOnCompass(boolean showDistanceOnCompass) {
        this.showDistanceOnCompass = showDistanceOnCompass;
    }

    public void setShowHealthBars(boolean showHealthBars) {
        this.showHealthBars = showHealthBars;
    }

    /**
     * Converts to simple JSON string for persistence.
     */
    @Nonnull
    public String toJson() {
        return String.format(
                "{\"MaxPartySize\":%d,\"InviteTimeoutSeconds\":%d,\"PvpProtectionEnabled\":%s,\"DefaultMarkerColor\":\"%s\",\"ShowDistanceOnCompass\":%s,\"ShowHealthBars\":%s}",
                maxPartySize, inviteTimeoutSeconds, pvpProtectionEnabled, defaultMarkerColor, showDistanceOnCompass, showHealthBars
        );
    }

    /**
     * Parses a PartyModConfig from a simple JSON string.
     */
    @Nonnull
    public static PartyModConfig fromJson(@Nonnull String json) {
        PartyModConfig config = new PartyModConfig();

        // Simple JSON parsing
        config.maxPartySize = extractJsonInt(json, "MaxPartySize", -1);
        config.inviteTimeoutSeconds = extractJsonInt(json, "InviteTimeoutSeconds", 60);
        config.pvpProtectionEnabled = extractJsonBoolean(json, "PvpProtectionEnabled", true);
        config.defaultMarkerColor = extractJsonString(json, "DefaultMarkerColor", "Blue");
        config.showDistanceOnCompass = extractJsonBoolean(json, "ShowDistanceOnCompass", true);
        config.showHealthBars = extractJsonBoolean(json, "ShowHealthBars", true);

        return config;
    }

    private static int extractJsonInt(String json, String key, int defaultValue) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) return defaultValue;
        start += pattern.length();
        int end = start;
        boolean negative = false;
        if (end < json.length() && json.charAt(end) == '-') {
            negative = true;
            end++;
        }
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean extractJsonBoolean(String json, String key, boolean defaultValue) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) return defaultValue;
        start += pattern.length();
        if (json.regionMatches(start, "true", 0, 4)) {
            return true;
        } else if (json.regionMatches(start, "false", 0, 5)) {
            return false;
        }
        return defaultValue;
    }

    private static String extractJsonString(String json, String key, String defaultValue) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return defaultValue;
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return defaultValue;
        return json.substring(start, end);
    }
}
