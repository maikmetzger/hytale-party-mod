package com.gaukh.partymod.data;

import com.gaukh.partymod.PartyMod;
import com.gaukh.partymod.party.Party;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Persistent storage for parties using JSON files.
 */
public class PartyDataStore {

    private final PartyMod plugin;
    private final Path dataDir;

    public PartyDataStore(@Nonnull PartyMod plugin) {
        this.plugin = plugin;
        this.dataDir = plugin.getDataDirectory().resolve("parties");

        // Create directory if it doesn't exist
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            plugin.getLogger().at(Level.SEVERE).withCause(e)
                    .log("Failed to create parties data directory");
        }
    }

    /**
     * Saves a party to disk.
     */
    public void save(@Nonnull String partyId, @Nonnull Party party) {
        Path file = dataDir.resolve(partyId + ".json");
        try {
            String json = party.toJson();
            Files.writeString(file, json);
        } catch (IOException e) {
            plugin.getLogger().at(Level.SEVERE).withCause(e)
                    .log("Failed to save party: %s", partyId);
        }
    }

    /**
     * Loads a party from disk.
     */
    @Nullable
    public Party load(@Nonnull String partyId) {
        Path file = dataDir.resolve(partyId + ".json");
        if (!Files.exists(file)) {
            return null;
        }

        try {
            String json = Files.readString(file);
            return Party.fromJson(json);
        } catch (Exception e) {
            plugin.getLogger().at(Level.SEVERE).withCause(e)
                    .log("Failed to load party: %s", partyId);
            return null;
        }
    }

    /**
     * Loads all parties from disk.
     */
    @Nonnull
    public Map<String, Party> loadAll() {
        Map<String, Party> result = new HashMap<>();

        if (!Files.exists(dataDir)) {
            return result;
        }

        try (Stream<Path> files = Files.list(dataDir)) {
            files.filter(p -> p.toString().endsWith(".json"))
                    .forEach(file -> {
                        String fileName = file.getFileName().toString();
                        String partyId = fileName.substring(0, fileName.length() - 5); // Remove .json

                        try {
                            String json = Files.readString(file);
                            Party party = Party.fromJson(json);
                            if (party != null) {
                                result.put(partyId, party);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().at(Level.WARNING).withCause(e)
                                    .log("Failed to load party file: %s", fileName);
                        }
                    });
        } catch (IOException e) {
            plugin.getLogger().at(Level.SEVERE).withCause(e)
                    .log("Failed to list party files");
        }

        return result;
    }

    /**
     * Deletes a party from disk.
     */
    public void delete(@Nonnull String partyId) {
        Path file = dataDir.resolve(partyId + ".json");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            plugin.getLogger().at(Level.WARNING).withCause(e)
                    .log("Failed to delete party file: %s", partyId);
        }
    }
}
