package com.gaukh.partymod.compat;

import com.buuz135.mhud.MultipleHUD;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

/**
 * Compatibility wrapper for the MultipleHUD mod.
 * <p>
 * This class provides a runtime check for MultipleHUD availability and
 * delegates HUD operations to it when present. If MultipleHUD is not
 * installed, the caller should fall back to the native Hytale HUD API.
 * <p>
 * Using MultipleHUD allows PartyMod to coexist with other HUD mods,
 * since vanilla Hytale only supports one custom HUD at a time.
 *
 * @see <a href="https://github.com/Buuz135/MHUD">MultipleHUD on GitHub</a>
 */
public class MultipleHudCompat {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Cached availability check result (null = not yet checked) */
    private static Boolean available = null;

    /** The HUD identifier used for PartyMod's HUD in MultipleHUD */
    public static final String PARTY_HUD_ID = "PartyHud";

    /**
     * Checks if the MultipleHUD mod is available at runtime.
     * The result is cached after the first check.
     *
     * @return true if MultipleHUD is installed and available
     */
    public static boolean isAvailable() {
        if (available == null) {
            try {
                Class.forName("com.buuz135.mhud.MultipleHUD");
                available = true;
                LOGGER.atInfo().log("[PartyMod] MultipleHUD detected - using multi-HUD compatibility mode");
            } catch (ClassNotFoundException e) {
                available = false;
                LOGGER.atInfo().log("[PartyMod] MultipleHUD not found - using native HUD mode");
            }
        }
        return available;
    }

    /**
     * Sets a custom HUD for a player using MultipleHUD.
     * Only call this if {@link #isAvailable()} returns true.
     *
     * @param player the player entity
     * @param playerRef the player reference
     * @param hudId unique identifier for this HUD
     * @param hud the CustomUIHud instance to display
     */
    public static void setCustomHud(@Nonnull Player player, @Nonnull PlayerRef playerRef,
                                     @Nonnull String hudId, @Nonnull CustomUIHud hud) {
        MultipleHUD.getInstance().setCustomHud(player, playerRef, hudId, hud);
    }

    /**
     * Hides/removes a custom HUD for a player using MultipleHUD.
     * Only call this if {@link #isAvailable()} returns true.
     *
     * @param player the player entity
     * @param playerRef the player reference
     * @param hudId the identifier of the HUD to hide
     */
    public static void hideCustomHud(@Nonnull Player player, @Nonnull PlayerRef playerRef,
                                      @Nonnull String hudId) {
        MultipleHUD.getInstance().hideCustomHud(player, playerRef, hudId);
    }
}
