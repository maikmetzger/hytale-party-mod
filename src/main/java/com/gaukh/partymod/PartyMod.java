package com.gaukh.partymod;

import com.gaukh.partymod.commands.PartyCommand;
import com.gaukh.partymod.party.PartyManager;
import com.gaukh.partymod.party.PartyStorage;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.logging.Level;

public class PartyMod extends JavaPlugin {

    private static PartyMod instance;
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

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

        partyManager = new PartyManager();
        getCommandRegistry().registerCommand(new PartyCommand(this));
        LOGGER.atInfo().log("PartyMod setup complete");
    }

    @Override
    protected void shutdown() {
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
