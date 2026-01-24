package com.gaukh.partymod;

import com.gaukh.partymod.commands.PartyCommand;
import com.gaukh.partymod.config.PlayerHudSettings;
import com.gaukh.partymod.events.PartyEventBus;
import com.gaukh.partymod.markers.PartyMarkerTicker;
import com.gaukh.partymod.party.PartyManager;
import com.gaukh.partymod.party.PartyPlayerListHud;
import com.gaukh.partymod.party.PartyStorage;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.asset.common.asset.FileCommonAsset;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

public class PartyMod extends JavaPlugin {

    private static PartyMod instance;
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final String PARTY_MARKER_ICON = "PartyMember.png";

    private PartyManager partyManager;

    public PartyMod(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
        LOGGER.atInfo().log("PartyMod loaded");
    }

    @Override
    protected void setup() {
        try {
            PartyStorage.init();
            LOGGER.atInfo().log("PartyStorage initialized");
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to initialize PartyStorage");
        }

        // Load player HUD settings
        PlayerHudSettings.load();

        // Register party member icon with CommonAssetModule
        registerPartyMemberIcon();

        partyManager = new PartyManager();
        getCommandRegistry().registerCommand(new PartyCommand(this));

        // Register ticker for party markers on main thread
        registerTicker();

        LOGGER.atInfo().log("PartyMod setup complete");
    }

    private void registerTicker() {
        // Register TickingSystem with EntityStoreRegistry
        getEntityStoreRegistry().registerSystem(PartyMarkerTicker.getInstance());
        LOGGER.atInfo().log("Registered PartyMarkerTicker as TickingSystem");

        // Initialize and register HUD ticker
        PartyPlayerListHud.getInstance().init();
        getEntityStoreRegistry().registerSystem(PartyPlayerListHud.getInstance());
        LOGGER.atInfo().log("Registered PartyPlayerListHud as TickingSystem");
    }

    private void registerPartyMemberIcon() {
        try {
            // Create icons directory in plugin data folder
            Path iconsDir = getDataDirectory().resolve("icons");
            Files.createDirectories(iconsDir);

            // Copy icon from resources to filesystem
            Path iconFile = iconsDir.resolve("PartyMember.png");
            if (!Files.exists(iconFile)) {
                try (InputStream is = getClass().getResourceAsStream("/Common/UI/WorldMap/MapMarkers/PartyMember.png")) {
                    if (is == null) {
                        LOGGER.atWarning().log("PartyMember.png not found in resources");
                        return;
                    }
                    Files.copy(is, iconFile);
                    LOGGER.atInfo().log("Copied PartyMember.png to %s", iconFile);
                }
            }

            // Read icon bytes
            byte[] iconBytes = Files.readAllBytes(iconFile);

            // Register with CommonAssetModule (following Waypoints pattern)
            FileCommonAsset asset = new FileCommonAsset(
                    iconFile,
                    PARTY_MARKER_ICON,
                    iconBytes
            );
            CommonAssetModule.get().addCommonAsset(PARTY_MARKER_ICON, asset);
            LOGGER.atInfo().log("Registered party member icon: %s", PARTY_MARKER_ICON);

        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to register party member icon");
        }
    }

    @Override
    protected void shutdown() {
        // Cleanup HUD system
        PartyPlayerListHud.getInstance().shutdown();

        // Save player HUD settings
        PlayerHudSettings.save();

        // Clear all event listeners
        PartyEventBus.clearListeners();

        PartyStorage.close();
        LOGGER.atInfo().log("PartyMod shutdown complete");
    }

    @Nonnull
    public static PartyMod getInstance() {
        return instance;
    }

    @Nonnull
    public PartyManager getPartyManager() {
        return partyManager;
    }
}
