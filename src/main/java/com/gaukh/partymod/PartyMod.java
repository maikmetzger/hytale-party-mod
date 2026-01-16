package com.gaukh.partymod;

import com.gaukh.partymod.commands.MarkerCommand;
import com.gaukh.partymod.commands.PartyCommand;
import com.gaukh.partymod.config.PartyModConfig;
import com.gaukh.partymod.marker.MarkerManager;
import com.gaukh.partymod.party.PartyManager;
import com.gaukh.partymod.tracking.PartyNameplateManager;
import com.gaukh.partymod.tracking.PartyTracker;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.logging.Level;

/**
 * PartyMod - A Hytale plugin for party management and map markers.
 */
public class PartyMod extends JavaPlugin {

    private static PartyMod instance;

    private PartyModConfig config;
    private PartyManager partyManager;
    private MarkerManager markerManager;
    private PartyTracker partyTracker;
    private PartyNameplateManager nameplateManager;

    public PartyMod(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        getLogger().at(Level.INFO).log("PartyMod setup starting...");

        // Initialize configuration
        this.config = new PartyModConfig();

        // Initialize managers
        partyManager = new PartyManager(this);
        markerManager = new MarkerManager(this);
        partyTracker = new PartyTracker(this);
        nameplateManager = new PartyNameplateManager(this);

        // Register commands
        getCommandRegistry().registerCommand(new PartyCommand(this));
        getCommandRegistry().registerCommand(new MarkerCommand(this));

        // Register event listeners
        partyManager.registerEvents();
        markerManager.registerEvents();
        partyTracker.registerEvents();
        nameplateManager.registerEvents();

        getLogger().at(Level.INFO).log("PartyMod setup complete!");
    }

    @Override
    protected void start() {
        getLogger().at(Level.INFO).log("PartyMod starting...");

        // Load persistent data
        partyManager.loadData();
        markerManager.loadData();

        getLogger().at(Level.INFO).log("PartyMod started successfully!");
    }

    @Override
    protected void shutdown() {
        getLogger().at(Level.INFO).log("PartyMod shutting down...");

        // Save all data
        if (partyManager != null) {
            partyManager.saveData();
        }
        if (markerManager != null) {
            markerManager.saveData();
        }

        getLogger().at(Level.INFO).log("PartyMod shutdown complete!");
    }

    // Getters

    @Nonnull
    public static PartyMod getInstance() {
        return instance;
    }

    @Nonnull
    public PartyModConfig getPluginConfig() {
        return config;
    }

    @Nonnull
    public PartyManager getPartyManager() {
        return partyManager;
    }

    @Nonnull
    public MarkerManager getMarkerManager() {
        return markerManager;
    }

    @Nonnull
    public PartyTracker getPartyTracker() {
        return partyTracker;
    }

    @Nonnull
    public PartyNameplateManager getNameplateManager() {
        return nameplateManager;
    }

    @Nonnull
    public Path getDataDirectory() {
        // Return a path for data storage
        return Path.of("plugins/PartyMod");
    }
}
