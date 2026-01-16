package com.gaukh.partymod.marker;

import com.gaukh.partymod.PartyMod;
import com.gaukh.partymod.party.Party;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manages all map markers.
 */
public class MarkerManager {

    private final PartyMod plugin;
    private final Path markersDir;

    // Marker ID -> Marker
    private final Map<String, Marker> markers = new ConcurrentHashMap<>();

    // Owner UUID -> List of marker IDs (for quick lookup)
    private final Map<UUID, Set<String>> ownerMarkerMap = new ConcurrentHashMap<>();

    public MarkerManager(@Nonnull PartyMod plugin) {
        this.plugin = plugin;
        this.markersDir = plugin.getDataDirectory().resolve("markers");

        // Create directory if it doesn't exist
        try {
            Files.createDirectories(markersDir);
        } catch (IOException e) {
            plugin.getLogger().at(Level.SEVERE).withCause(e)
                    .log("Failed to create markers data directory");
        }
    }

    public void registerEvents() {
        // No events needed for markers currently
    }

    public void loadData() {
        markers.clear();
        ownerMarkerMap.clear();

        if (!Files.exists(markersDir)) {
            return;
        }

        try (Stream<Path> files = Files.list(markersDir)) {
            files.filter(p -> p.toString().endsWith(".json"))
                    .forEach(file -> {
                        try {
                            String json = Files.readString(file);
                            Marker marker = Marker.fromJson(json);
                            if (marker != null) {
                                markers.put(marker.getId(), marker);
                                ownerMarkerMap.computeIfAbsent(marker.getOwnerUuid(),
                                        k -> ConcurrentHashMap.newKeySet()).add(marker.getId());
                            }
                        } catch (Exception e) {
                            plugin.getLogger().at(Level.WARNING).withCause(e)
                                    .log("Failed to load marker file: %s", file.getFileName());
                        }
                    });
        } catch (IOException e) {
            plugin.getLogger().at(Level.SEVERE).withCause(e)
                    .log("Failed to list marker files");
        }

        plugin.getLogger().at(Level.INFO).log("Loaded %d markers", markers.size());
    }

    public void saveData() {
        for (Marker marker : markers.values()) {
            saveMarker(marker);
        }
        plugin.getLogger().at(Level.INFO).log("Saved %d markers", markers.size());
    }

    private void saveMarker(@Nonnull Marker marker) {
        Path file = markersDir.resolve(marker.getId() + ".json");
        try {
            String json = marker.toJson();
            Files.writeString(file, json);
        } catch (IOException e) {
            plugin.getLogger().at(Level.SEVERE).withCause(e)
                    .log("Failed to save marker: %s", marker.getId());
        }
    }

    private void deleteMarkerFile(@Nonnull String markerId) {
        Path file = markersDir.resolve(markerId + ".json");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            plugin.getLogger().at(Level.WARNING).withCause(e)
                    .log("Failed to delete marker file: %s", markerId);
        }
    }

    // CRUD Operations

    /**
     * Creates a new marker.
     *
     * @return The created marker
     */
    @Nonnull
    public Marker createMarker(@Nonnull Marker marker) {
        markers.put(marker.getId(), marker);
        ownerMarkerMap.computeIfAbsent(marker.getOwnerUuid(),
                k -> ConcurrentHashMap.newKeySet()).add(marker.getId());
        saveMarker(marker);
        return marker;
    }

    /**
     * Gets a marker by ID.
     */
    @Nullable
    public Marker getMarker(@Nonnull String markerId) {
        return markers.get(markerId);
    }

    /**
     * Updates a marker.
     */
    public void updateMarker(@Nonnull Marker marker) {
        if (markers.containsKey(marker.getId())) {
            markers.put(marker.getId(), marker);
            saveMarker(marker);
        }
    }

    /**
     * Deletes a marker.
     *
     * @return true if deleted successfully
     */
    public boolean deleteMarker(@Nonnull String markerId) {
        Marker marker = markers.remove(markerId);
        if (marker == null) {
            return false;
        }

        Set<String> ownerMarkers = ownerMarkerMap.get(marker.getOwnerUuid());
        if (ownerMarkers != null) {
            ownerMarkers.remove(markerId);
        }

        deleteMarkerFile(markerId);
        return true;
    }

    /**
     * Gets all markers owned by a player.
     */
    @Nonnull
    public List<Marker> getMarkersByOwner(@Nonnull UUID ownerUuid) {
        Set<String> markerIds = ownerMarkerMap.get(ownerUuid);
        if (markerIds == null || markerIds.isEmpty()) {
            return Collections.emptyList();
        }

        return markerIds.stream()
                .map(markers::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Gets all markers visible to a player.
     * This includes:
     * - Their own markers (PRIVATE, PARTY, GLOBAL)
     * - Party members' PARTY markers
     * - Everyone's GLOBAL markers
     */
    @Nonnull
    public List<Marker> getVisibleMarkers(@Nonnull UUID viewerUuid) {
        Party viewerParty = plugin.getPartyManager().getPartyByPlayer(viewerUuid);
        Set<UUID> partyMembers = viewerParty != null ? viewerParty.getMemberUuids() : Collections.emptySet();

        return markers.values().stream()
                .filter(marker -> isMarkerVisible(marker, viewerUuid, partyMembers))
                .collect(Collectors.toList());
    }

    /**
     * Checks if a marker is visible to a viewer.
     */
    public boolean isMarkerVisible(@Nonnull Marker marker, @Nonnull UUID viewerUuid,
                                   @Nonnull Set<UUID> partyMembers) {
        // Owner can always see their markers
        if (marker.isOwner(viewerUuid)) {
            return true;
        }

        switch (marker.getVisibility()) {
            case PRIVATE:
                return false;
            case PARTY:
                // Visible if owner is in viewer's party
                return partyMembers.contains(marker.getOwnerUuid());
            case GLOBAL:
                return true;
            default:
                return false;
        }
    }

    /**
     * Gets all markers (for admin purposes).
     */
    @Nonnull
    public Collection<Marker> getAllMarkers() {
        return Collections.unmodifiableCollection(markers.values());
    }

    /**
     * Finds a marker by name (owned by a specific player).
     */
    @Nullable
    public Marker findMarkerByName(@Nonnull UUID ownerUuid, @Nonnull String name) {
        return getMarkersByOwner(ownerUuid).stream()
                .filter(m -> m.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets the count of markers owned by a player.
     */
    public int getMarkerCount(@Nonnull UUID ownerUuid) {
        Set<String> markerIds = ownerMarkerMap.get(ownerUuid);
        return markerIds != null ? markerIds.size() : 0;
    }
}
