package com.gaukh.partymod.markers;

import com.gaukh.partymod.PartyMod;
import com.gaukh.partymod.party.FakeMember;
import com.gaukh.partymod.party.Party;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PositionUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages party member markers using TickingSystem.
 * Runs on MAIN THREAD - safe to call getComponent() without console spam.
 */
public class PartyMarkerTicker extends TickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final PartyMarkerTicker INSTANCE = new PartyMarkerTicker();

    private float accumulator = 0f;
    private static final float UPDATE_INTERVAL = 0.1f; // 100ms between updates (efficient due to delta logic)

    // Delta thresholds for position and rotation changes
    private static final double POSITION_THRESHOLD = 5.0; // 5 blocks minimum movement
    private static final double POSITION_THRESHOLD_SQ = POSITION_THRESHOLD * POSITION_THRESHOLD;
    private static final float YAW_THRESHOLD = 0.05f; // ~2.86 degrees

    // Track marker state for each viewer to enable delta updates
    private final Map<UUID, Map<String, MarkerState>> displayedMarkers = new ConcurrentHashMap<>();

    /**
     * Tracks the last known state of a marker to enable delta updates.
     * Only sends updates when position, name, or yaw changes significantly.
     */
    private static class MarkerState {
        final String id;
        String name;
        double x, y, z;
        float yaw;

        MarkerState(String id, String name, double x, double y, double z, float yaw) {
            this.id = id;
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
        }

        /**
         * Check if the marker needs an update based on position, name, or yaw changes.
         */
        boolean needsUpdate(String newName, double newX, double newY, double newZ, float newYaw) {
            // Check name change (distance in name changes frequently)
            if (!this.name.equals(newName)) {
                return true;
            }

            // Check position change (squared distance for efficiency)
            double dx = newX - this.x;
            double dy = newY - this.y;
            double dz = newZ - this.z;
            if (dx * dx + dy * dy + dz * dz >= POSITION_THRESHOLD_SQ) {
                return true;
            }

            // Check yaw change
            if (Math.abs(newYaw - this.yaw) >= YAW_THRESHOLD) {
                return true;
            }

            return false;
        }

        /**
         * Update the cached state with new values.
         */
        void update(String newName, double newX, double newY, double newZ, float newYaw) {
            this.name = newName;
            this.x = newX;
            this.y = newY;
            this.z = newZ;
            this.yaw = newYaw;
        }
    }

    public static PartyMarkerTicker getInstance() {
        return INSTANCE;
    }

    @Override
    public void tick(float dt, int index, Store<EntityStore> store) {
        accumulator += dt;
        if (accumulator >= UPDATE_INTERVAL) {
            accumulator = 0f;
            updateAllPartyMarkers(store);
        }
    }

    /**
     * Update markers for all players in the current world.
     * Uses currentWorld.getPlayers() to avoid cross-thread access issues.
     * This ensures we only process players on the correct thread.
     */
    @SuppressWarnings("deprecation") // getPlayers() is deprecated but necessary for thread-safety
    private void updateAllPartyMarkers(Store<EntityStore> store) {
        // Get current world from store - this is the world this ticker is running on
        World currentWorld = store.getExternalData().getWorld();
        if (currentWorld == null) {
            return;
        }

        // IMPORTANT: Only iterate over players in THIS world to avoid cross-thread access!
        // Universe.get().getPlayers() returns ALL players across ALL worlds, causing
        // "PlayerRef.getComponent() called async with player in world" errors.
        for (Player viewer : currentWorld.getPlayers()) {
            try {
                // Get PlayerRef from the Player's UUID
                PlayerRef viewerRef = Universe.get().getPlayer(viewer.getUuid());
                if (viewerRef == null) continue;

                updateMarkersForPlayer(viewerRef, viewer, store);
            } catch (Exception e) {
                // Ignore errors for individual players (they may not be fully initialized)
            }
        }
    }

    private void updateMarkersForPlayer(PlayerRef viewerRef, Player viewer, Store<EntityStore> store) {
        UUID viewerUuid = viewerRef.getUuid();
        World viewerWorld = viewer.getWorld();
        if (viewerWorld == null) return;

        // Check if player is in a party
        Party party = PartyMod.getInstance().getPartyManager().getPartyByPlayer(viewerUuid);
        if (party == null) {
            removeAllMarkersForPlayer(viewerUuid, viewerRef);
            return;
        }

        // Get viewer position for distance calculation
        TransformComponent viewerTransform = viewer.getTransformComponent();
        if (viewerTransform == null) return;
        double viewerX = viewerTransform.getTransform().getPosition().getX();
        double viewerY = viewerTransform.getTransform().getPosition().getY();
        double viewerZ = viewerTransform.getTransform().getPosition().getZ();

        // Get or create the marker state map for this viewer
        Map<String, MarkerState> viewerMarkerStates = displayedMarkers.computeIfAbsent(
                viewerUuid, k -> new ConcurrentHashMap<>()
        );

        Set<String> currentMarkerIds = new HashSet<>();
        List<MapMarker> markersToSend = new ArrayList<>();

        // Build markers for all party members except the viewer
        for (UUID memberUuid : party.getMembersExcept(viewerUuid)) {
            PlayerRef memberRef = Universe.get().getPlayer(memberUuid);
            if (memberRef == null) continue;

            // Get member player - only safe if they're in the same world
            // For cross-world members, we skip them (no marker shown)
            Player memberPlayer = getMemberPlayerSafe(memberRef, viewerWorld);
            if (memberPlayer == null) continue;

            TransformComponent transform = memberPlayer.getTransformComponent();
            if (transform == null) continue;

            // Get member position and yaw
            double memberX = transform.getTransform().getPosition().getX();
            double memberY = transform.getTransform().getPosition().getY();
            double memberZ = transform.getTransform().getPosition().getZ();
            float memberYaw = transform.getTransform().getRotation().getYaw();

            // Calculate distance to viewer
            double dx = memberX - viewerX;
            double dy = memberY - viewerY;
            double dz = memberZ - viewerZ;
            int distance = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);

            String markerId = "party-member-" + memberUuid.toString();
            currentMarkerIds.add(markerId);

            String markerName = memberRef.getUsername() + " (" + distance + "m)";

            // Check if this marker needs an update (delta logic)
            MarkerState existingState = viewerMarkerStates.get(markerId);
            boolean needsUpdate = (existingState == null) ||
                    existingState.needsUpdate(markerName, memberX, memberY, memberZ, memberYaw);

            if (needsUpdate) {
                // Create or update the marker state
                if (existingState == null) {
                    viewerMarkerStates.put(markerId, new MarkerState(
                            markerId, markerName, memberX, memberY, memberZ, memberYaw
                    ));
                } else {
                    existingState.update(markerName, memberX, memberY, memberZ, memberYaw);
                }

                // Add marker to send list
                MapMarker marker = new MapMarker(
                        markerId,
                        markerName,
                        PartyMod.PARTY_MARKER_ICON,
                        PositionUtil.toTransformPacket(transform.getTransform()),
                        null
                );
                markersToSend.add(marker);
            }
        }

        // Build markers for fake members (testing)
        for (FakeMember fakeMember : party.getFakeMembers().values()) {
            // Update fake member movement (simulates walking around)
            boolean moved = fakeMember.updateMovement();

            double memberX = fakeMember.getX();
            double memberY = fakeMember.getY();
            double memberZ = fakeMember.getZ();
            float memberYaw = fakeMember.getYaw();

            // Update NPC entity position if it exists and moved
            if (moved && fakeMember.hasEntity()) {
                try {
                    Ref<EntityStore> entityRef = fakeMember.getEntityRef();
                    if (entityRef != null && entityRef.isValid()) {
                        TransformComponent npcTransform = store.getComponent(entityRef, TransformComponent.getComponentType());
                        if (npcTransform != null) {
                            Vector3d npcPosition = npcTransform.getPosition();
                            npcPosition.x = memberX;
                            npcPosition.y = memberY;
                            npcPosition.z = memberZ;
                        }
                    }
                } catch (Exception e) {
                    // Ignore entity update errors
                }
            }

            // Calculate distance to viewer
            double dx = memberX - viewerX;
            double dy = memberY - viewerY;
            double dz = memberZ - viewerZ;
            int distance = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);

            String markerId = "party-fake-" + fakeMember.getUuid().toString();
            currentMarkerIds.add(markerId);

            String markerName = fakeMember.getName() + " (" + distance + "m)";

            // Check if this marker needs an update (delta logic)
            MarkerState existingState = viewerMarkerStates.get(markerId);
            boolean needsUpdate = (existingState == null) ||
                    existingState.needsUpdate(markerName, memberX, memberY, memberZ, memberYaw);

            if (needsUpdate) {
                // Create or update the marker state
                if (existingState == null) {
                    viewerMarkerStates.put(markerId, new MarkerState(
                            markerId, markerName, memberX, memberY, memberZ, memberYaw
                    ));
                } else {
                    existingState.update(markerName, memberX, memberY, memberZ, memberYaw);
                }

                // Create transform for fake member position
                com.hypixel.hytale.math.vector.Transform fakeTransform =
                    new com.hypixel.hytale.math.vector.Transform(memberX, memberY, memberZ);

                // Add marker to send list
                MapMarker marker = new MapMarker(
                        markerId,
                        markerName,
                        PartyMod.PARTY_MARKER_ICON,
                        PositionUtil.toTransformPacket(fakeTransform),
                        null
                );
                markersToSend.add(marker);
            }
        }

        // Determine which markers need to be removed
        List<String> markersToRemove = new ArrayList<>();
        for (String existingMarkerId : viewerMarkerStates.keySet()) {
            if (!currentMarkerIds.contains(existingMarkerId)) {
                markersToRemove.add(existingMarkerId);
            }
        }

        // Remove stale marker states
        for (String removedId : markersToRemove) {
            viewerMarkerStates.remove(removedId);
        }

        // Clean up empty viewer entries
        if (viewerMarkerStates.isEmpty()) {
            displayedMarkers.remove(viewerUuid);
        }

        // Send packet only if there are actual changes
        if (!markersToSend.isEmpty() || !markersToRemove.isEmpty()) {
            sendUpdateWorldMapPacket(viewerRef, markersToSend, markersToRemove);
        }
    }

    /**
     * Safely gets a Player component for a member, only if they're in the same world as the viewer.
     * Returns null if the member is offline, not loaded, or in a different world.
     *
     * Note: If the member is in a different world, Hytale will log a warning about cross-thread access,
     * but this is unavoidable as we cannot check the world without calling getComponent().
     * The warning is harmless - it just indicates we tried to access a player in another world.
     */
    @SuppressWarnings("deprecation")
    private Player getMemberPlayerSafe(PlayerRef memberRef, World viewerWorld) {
        // Unfortunately, there's no way to check which world a player is in without calling getComponent().
        // If the player is in a different world, Hytale logs a warning but returns null.
        Player memberPlayer = memberRef.getComponent(Player.getComponentType());
        if (memberPlayer == null) {
            return null;
        }

        // Check if member is in the same world as the viewer
        World memberWorld = memberPlayer.getWorld();
        if (memberWorld == null || !viewerWorld.equals(memberWorld)) {
            return null;
        }

        return memberPlayer;
    }

    private void removeAllMarkersForPlayer(UUID playerUuid, PlayerRef playerRef) {
        Map<String, MarkerState> existingMarkerStates = displayedMarkers.remove(playerUuid);

        if (existingMarkerStates != null && !existingMarkerStates.isEmpty()) {
            if (playerRef != null) {
                sendUpdateWorldMapPacket(playerRef, Collections.emptyList(), new ArrayList<>(existingMarkerStates.keySet()));
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
     * Called when a player leaves a party.
     * Removes the leaving player's marker from all party members' views.
     */
    public void onPlayerLeaveParty(UUID playerUuid, Party party) {
        if (party == null) return;

        String markerId = "party-member-" + playerUuid.toString();

        // Remove the leaving player's marker from all other party members' views
        for (UUID memberUuid : party.getMemberUuids()) {
            if (memberUuid.equals(playerUuid)) continue;

            Map<String, MarkerState> markerStates = displayedMarkers.get(memberUuid);
            if (markerStates != null) {
                markerStates.remove(markerId);
            }

            PlayerRef memberRef = Universe.get().getPlayer(memberUuid);
            if (memberRef != null) {
                sendUpdateWorldMapPacket(memberRef, Collections.emptyList(), Collections.singletonList(markerId));
            }
        }

        // Remove all markers for the leaving player
        displayedMarkers.remove(playerUuid);
    }
}
