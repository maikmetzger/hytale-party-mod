package com.gaukh.partymod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side player settings for the Party HUD.
 * Stored locally as JSON, not synced to server database.
 */
public class PlayerHudSettings {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String CONFIG_PATH = "mods/PartyMod/player_hud_settings.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<UUID, HudSettings> playerSettings = new ConcurrentHashMap<>();
    private static boolean loaded = false;

    /**
     * Ordering mode for HUD members.
     */
    public enum OrderMode {
        FIXED,      // Join order (current behavior)
        DISTANCE    // Closest players first
    }

    /**
     * HUD settings for a single player.
     */
    public static class HudSettings {
        public boolean showHud = true;
        public boolean showSelf = true;
        public int maxDisplayedMembers = 8;
        public OrderMode orderMode = OrderMode.FIXED;

        public HudSettings() {}

        public HudSettings(boolean showHud, boolean showSelf, int maxDisplayedMembers, OrderMode orderMode) {
            this.showHud = showHud;
            this.showSelf = showSelf;
            this.maxDisplayedMembers = Math.max(1, Math.min(8, maxDisplayedMembers));
            this.orderMode = orderMode;
        }

        /**
         * Clamps maxDisplayedMembers to valid range (1-8).
         */
        public void validate() {
            maxDisplayedMembers = Math.max(1, Math.min(8, maxDisplayedMembers));
            if (orderMode == null) {
                orderMode = OrderMode.FIXED;
            }
        }
    }

    /**
     * Load settings from disk on startup.
     */
    public static void load() {
        if (loaded) return;

        Path path = Paths.get(CONFIG_PATH);
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                Type type = new TypeToken<Map<UUID, HudSettings>>(){}.getType();
                Map<UUID, HudSettings> loadedSettings = GSON.fromJson(reader, type);
                if (loadedSettings != null) {
                    // Validate each entry
                    loadedSettings.values().forEach(HudSettings::validate);
                    playerSettings.putAll(loadedSettings);
                    LOGGER.atInfo().log("Loaded HUD settings for %d players", playerSettings.size());
                }
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to load player HUD settings");
            }
        }
        loaded = true;
    }

    /**
     * Save all settings to disk.
     */
    public static void save() {
        try {
            Path path = Paths.get(CONFIG_PATH);
            Files.createDirectories(path.getParent());

            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(playerSettings, writer);
            }
            LOGGER.atInfo().log("Saved HUD settings for %d players", playerSettings.size());
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to save player HUD settings");
        }
    }

    /**
     * Get settings for a player, creating defaults if none exist.
     */
    @Nonnull
    public static HudSettings get(@Nonnull UUID playerUuid) {
        return playerSettings.computeIfAbsent(playerUuid, k -> new HudSettings());
    }

    /**
     * Update settings for a player and save immediately.
     */
    public static void update(@Nonnull UUID playerUuid, @Nonnull HudSettings settings) {
        settings.validate();
        playerSettings.put(playerUuid, settings);
        save();
    }

    /**
     * Update a single setting and save.
     */
    public static void setShowHud(@Nonnull UUID playerUuid, boolean showHud) {
        HudSettings settings = get(playerUuid);
        settings.showHud = showHud;
        save();
    }

    public static void setShowSelf(@Nonnull UUID playerUuid, boolean showSelf) {
        HudSettings settings = get(playerUuid);
        settings.showSelf = showSelf;
        save();
    }

    public static void setMaxDisplayedMembers(@Nonnull UUID playerUuid, int max) {
        HudSettings settings = get(playerUuid);
        settings.maxDisplayedMembers = Math.max(1, Math.min(8, max));
        save();
    }

    public static void setOrderMode(@Nonnull UUID playerUuid, OrderMode mode) {
        HudSettings settings = get(playerUuid);
        settings.orderMode = mode;
        save();
    }

    /**
     * Check if a warning should be shown (party size > max displayed and not using distance mode).
     */
    public static boolean shouldShowWarning(@Nonnull UUID playerUuid, int partyMemberCount) {
        HudSettings settings = get(playerUuid);
        return partyMemberCount > settings.maxDisplayedMembers
               && settings.orderMode != OrderMode.DISTANCE;
    }
}
