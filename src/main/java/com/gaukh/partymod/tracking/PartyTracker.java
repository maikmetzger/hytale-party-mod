package com.gaukh.partymod.tracking;

import com.gaukh.partymod.PartyMod;
import com.gaukh.partymod.marker.Marker;
import com.gaukh.partymod.party.Party;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.logging.Level;

/**
 * Tracks party members and markers for compass/map display.
 *
 * Note: This is a simplified implementation. The actual WorldMapManager.MarkerProvider
 * integration would require more complex setup depending on Hytale's API availability.
 */
public class PartyTracker {

    private final PartyMod plugin;

    public PartyTracker(@Nonnull PartyMod plugin) {
        this.plugin = plugin;
    }

    public void registerEvents() {
        // TODO: Implement periodic compass updates when WorldMapTracker API is available
        // Currently, this is a placeholder for future implementation
        plugin.getLogger().at(Level.INFO).log("PartyTracker initialized");
    }

    /**
     * Calculates the 3D distance between two positions.
     */
    public double calculateDistance(double[] pos1, double[] pos2) {
        double dx = pos1[0] - pos2[0];
        double dy = pos1[1] - pos2[1];
        double dz = pos1[2] - pos2[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Gets a player's current position as [x, y, z].
     */
    @Nullable
    public double[] getPlayerPosition(@Nonnull PlayerRef playerRef) {
        try {
            return new double[] {
                    playerRef.getTransform().getPosition().getX(),
                    playerRef.getTransform().getPosition().getY(),
                    playerRef.getTransform().getPosition().getZ()
            };
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets all visible markers for a player (for map display).
     */
    @Nonnull
    public List<MarkerInfo> getVisibleMarkersForPlayer(@Nonnull UUID playerUuid) {
        List<MarkerInfo> result = new ArrayList<>();

        // Add party member markers
        Party party = plugin.getPartyManager().getPartyByPlayer(playerUuid);
        if (party != null) {
            for (UUID memberUuid : party.getMembersExcept(playerUuid)) {
                PlayerRef memberRef = Universe.get().getPlayer(memberUuid);
                if (memberRef == null) continue;

                double[] memberPos = getPlayerPosition(memberRef);
                if (memberPos == null) continue;

                result.add(new MarkerInfo(
                        "party_" + memberUuid,
                        memberRef.getUsername(),
                        memberPos[0], memberPos[1], memberPos[2],
                        "marker_party.png",
                        MarkerInfo.Type.PARTY_MEMBER
                ));
            }
        }

        // Add custom markers
        List<Marker> markers = plugin.getMarkerManager().getVisibleMarkers(playerUuid);
        for (Marker marker : markers) {
            result.add(new MarkerInfo(
                    marker.getId(),
                    marker.getName(),
                    marker.getX(), marker.getY(), marker.getZ(),
                    marker.getColor().getImagePath(),
                    MarkerInfo.Type.CUSTOM_MARKER
            ));
        }

        return result;
    }

    /**
     * Represents a marker for display purposes.
     */
    public static class MarkerInfo {
        public enum Type {
            PARTY_MEMBER,
            CUSTOM_MARKER
        }

        private final String id;
        private final String name;
        private final double x;
        private final double y;
        private final double z;
        private final String iconPath;
        private final Type type;

        public MarkerInfo(@Nonnull String id, @Nonnull String name,
                          double x, double y, double z,
                          @Nonnull String iconPath,
                          @Nonnull Type type) {
            this.id = id;
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.iconPath = iconPath;
            this.type = type;
        }

        @Nonnull
        public String getId() {
            return id;
        }

        @Nonnull
        public String getName() {
            return name;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }

        @Nonnull
        public String getIconPath() {
            return iconPath;
        }

        @Nonnull
        public Type getType() {
            return type;
        }
    }
}
