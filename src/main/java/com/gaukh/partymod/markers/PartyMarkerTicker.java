package com.gaukh.partymod.markers;

import com.gaukh.partymod.PartyMod;
import com.gaukh.partymod.party.Party;
import com.hypixel.hytale.common.thread.ticking.Tickable;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.PositionUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages party member markers using Tickable interface.
 * Runs on MAIN THREAD - safe to call getComponent() without console spam.
 */
public class PartyMarkerTicker implements Tickable {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final PartyMarkerTicker INSTANCE = new PartyMarkerTicker();

    private float accumulator = 0f;
    private static final float UPDATE_INTERVAL = 0.5f; // 500ms between updates

    // Track which markers each player currently has displayed
    private final Map<UUID, Set<String>> displayedMarkers = new ConcurrentHashMap<>();

    public static PartyMarkerTicker getInstance() {
        return INSTANCE;
    }

    @Override
    public void tick(float deltaTime) {
        accumulator += deltaTime;
        if (accumulator >= UPDATE_INTERVAL) {
            accumulator = 0f;
            updateAllPartyMarkers();
        }
    }

    /**
     * Update markers for all online players.
     * Runs on MAIN THREAD - safe to call getComponent().
     */
    private void updateAllPartyMarkers() {
        for (PlayerRef viewerRef : Universe.get().getPlayers()) {
            updateMarkersForPlayer(viewerRef);
        }
    }

    private void updateMarkersForPlayer(PlayerRef viewerRef) {
        UUID viewerUuid = viewerRef.getUuid();

        // Check if player is in a party
        Party party = PartyMod.getInstance().getPartyManager().getPartyByPlayer(viewerUuid);
        if (party == null) {
            removeAllMarkersForPlayer(viewerUuid, viewerRef);
            return;
        }

        // Get viewer's player component
        Player viewer = viewerRef.getComponent(Player.getComponentType());
        if (viewer == null) {
            return;
        }

        // Get viewer position for distance calculation
        TransformComponent viewerTransform = viewer.getTransformComponent();
        if (viewerTransform == null) return;
        double viewerX = viewerTransform.getTransform().getPosition().getX();
        double viewerY = viewerTransform.getTransform().getPosition().getY();
        double viewerZ = viewerTransform.getTransform().getPosition().getZ();

        World viewerWorld = viewer.getWorld();

        Set<String> currentMarkerIds = new HashSet<>();
        List<MapMarker> markersToSend = new ArrayList<>();

        // Build markers for all party members except the viewer
        for (UUID memberUuid : party.getMembersExcept(viewerUuid)) {
            PlayerRef memberRef = Universe.get().getPlayer(memberUuid);
            if (memberRef == null) continue;

            Player memberPlayer = memberRef.getComponent(Player.getComponentType());
            if (memberPlayer == null) continue;

            // Check if in same world
            if (viewerWorld != null && !viewerWorld.equals(memberPlayer.getWorld())) {
                continue;
            }

            TransformComponent transform = memberPlayer.getTransformComponent();
            if (transform == null) continue;

            // Calculate distance
            double memberX = transform.getTransform().getPosition().getX();
            double memberY = transform.getTransform().getPosition().getY();
            double memberZ = transform.getTransform().getPosition().getZ();
            double dx = memberX - viewerX;
            double dy = memberY - viewerY;
            double dz = memberZ - viewerZ;
            int distance = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);

            String markerId = "party-member-" + memberUuid.toString();
            currentMarkerIds.add(markerId);

            String markerName = memberRef.getUsername() + " (" + distance + "m)";

            MapMarker marker = new MapMarker(
                    markerId,
                    markerName,
                    PartyMod.PARTY_MARKER_ICON,
                    PositionUtil.toTransformPacket(transform.getTransform()),
                    null
            );
            markersToSend.add(marker);
        }

        // Determine which markers need to be removed
        Set<String> previousMarkers = displayedMarkers.getOrDefault(viewerUuid, Collections.emptySet());
        List<String> markersToRemove = new ArrayList<>();
        for (String oldMarkerId : previousMarkers) {
            if (!currentMarkerIds.contains(oldMarkerId)) {
                markersToRemove.add(oldMarkerId);
            }
        }

        // Update tracked markers
        if (currentMarkerIds.isEmpty()) {
            displayedMarkers.remove(viewerUuid);
        } else {
            displayedMarkers.put(viewerUuid, currentMarkerIds);
        }

        // Send packet directly on main thread
        if (!markersToSend.isEmpty() || !markersToRemove.isEmpty()) {
            sendUpdateWorldMapPacket(viewerRef, markersToSend, markersToRemove);
        }
    }

    private void removeAllMarkersForPlayer(UUID playerUuid, PlayerRef playerRef) {
        Set<String> existingMarkers = displayedMarkers.remove(playerUuid);

        if (existingMarkers != null && !existingMarkers.isEmpty()) {
            if (playerRef != null) {
                sendUpdateWorldMapPacket(playerRef, Collections.emptyList(), new ArrayList<>(existingMarkers));
            }
        }
    }

    private void sendUpdateWorldMapPacket(PlayerRef playerRef, List<MapMarker> addedMarkers, List<String> removedMarkerIds) {
        try {
            PacketHandler handler = playerRef.getPacketHandler();
            if (handler == null) return;

            UpdateWorldMap packet = new UpdateWorldMap(
                    null,
                    addedMarkers.toArray(new MapMarker[0]),
                    removedMarkerIds.toArray(new String[0])
            );

            handler.writePacket(packet, false);
        } catch (Exception e) {
            // Silently ignore packet send failures
        }
    }

    /**
     * Called when a player leaves a party
     */
    public void onPlayerLeaveParty(UUID playerUuid, Party party) {
        if (party == null) return;

        String markerId = "party-member-" + playerUuid.toString();

        for (UUID memberUuid : party.getMemberUuids()) {
            if (memberUuid.equals(playerUuid)) continue;

            Set<String> markers = displayedMarkers.get(memberUuid);
            if (markers != null) {
                markers.remove(markerId);
            }

            PlayerRef memberRef = Universe.get().getPlayer(memberUuid);
            if (memberRef != null) {
                sendUpdateWorldMapPacket(memberRef, Collections.emptyList(), Collections.singletonList(markerId));
            }
        }

        displayedMarkers.remove(playerUuid);
    }
}
