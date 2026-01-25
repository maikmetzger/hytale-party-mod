package com.gaukh.partymod.party;

import com.gaukh.partymod.PartyMod;
import com.gaukh.partymod.compat.MultipleHudCompat;
import com.gaukh.partymod.config.PlayerHudSettings;
import com.gaukh.partymod.events.*;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsModule;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HUD system for displaying party member information.
 * <p>
 * Uses a hybrid approach:
 * - Event-driven updates for party structure changes (create, join, leave, disband)
 * - Tick-based delta detection for continuous stats (health, stamina, distance)
 * <p>
 * Only updates the UI when something actually changes, avoiding constant polling.
 *
 * @see PartyEventBus for event dispatching
 * @see MemberHudState for tracking member stat changes
 */
public class PartyPlayerListHud extends TickingSystem<EntityStore> implements PartyEventListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final PartyPlayerListHud INSTANCE = new PartyPlayerListHud();

    // Tick accumulator for stat update interval
    private float accumulator = 0f;
    private static final float STAT_UPDATE_INTERVAL = 1.0f; // 1 update/sec for HUD stats

    // Separate accumulator for cleanup (runs less frequently)
    private float cleanupAccumulator = 0f;
    private static final float CLEANUP_INTERVAL = 3.0f; // Cleanup offline states every 3 seconds

    // Configurable minimum member count to show HUD (default 2, set to 1 for debug/solo)
    private static int minMembersForHud = 1;

    // Track HUD state per viewer
    private final Map<UUID, ViewerHudState> viewerStates = new ConcurrentHashMap<>();

    /**
     * Tracks the last known state of a party member for delta detection.
     * Only sends UI updates when values change significantly.
     */
    private static class MemberHudState {
        final UUID memberUuid;
        float lastHealth;
        float lastMaxHealth;
        float lastStamina;
        float lastMaxStamina;
        int lastDistance; // Rounded to avoid constant updates
        boolean lastOnline;
        String lastName;

        MemberHudState(@Nonnull UUID memberUuid) {
            this.memberUuid = memberUuid;
            this.lastHealth = -1;
            this.lastMaxHealth = -1;
            this.lastStamina = -1;
            this.lastMaxStamina = -1;
            this.lastDistance = -1;
            this.lastOnline = false;
            this.lastName = "";
        }

        /**
         * Check if any stat has changed enough to warrant a UI update.
         * Uses tolerances to avoid unnecessary updates:
         * - Health/Stamina: 0.1 tolerance (fine-grained for combat feedback)
         * - Distance: 5 block tolerance (reduces updates during movement)
         */
        boolean hasChanged(float health, float maxHealth, float stamina,
                           float maxStamina, int distance, boolean online, String name) {
            // Check for significant changes (health/stamina allow small tolerance)
            return Math.abs(health - lastHealth) > 0.1f
                    || Math.abs(maxHealth - lastMaxHealth) > 0.1f
                    || Math.abs(stamina - lastStamina) > 0.1f
                    || Math.abs(maxStamina - lastMaxStamina) > 0.1f
                    || Math.abs(distance - lastDistance) > 5  // 5 block tolerance for movement
                    || online != lastOnline
                    || !name.equals(lastName);
        }

        /**
         * Update cached state after sending UI update.
         */
        void update(float health, float maxHealth, float stamina,
                    float maxStamina, int distance, boolean online, String name) {
            this.lastHealth = health;
            this.lastMaxHealth = maxHealth;
            this.lastStamina = stamina;
            this.lastMaxStamina = maxStamina;
            this.lastDistance = distance;
            this.lastOnline = online;
            this.lastName = name;
        }
    }

    /**
     * Tracks overall HUD state for a single viewer.
     */
    private static class ViewerHudState {
        final UUID viewerUuid;
        String currentPartyId;
        boolean hudVisible = false;
        PartyMemberHud hudInstance; // The actual HUD registered with HudManager
        final Map<UUID, MemberHudState> memberStates = new ConcurrentHashMap<>();

        ViewerHudState(@Nonnull UUID viewerUuid) {
            this.viewerUuid = viewerUuid;
        }
    }

    private PartyPlayerListHud() {
        // Private constructor for singleton
    }

    public static PartyPlayerListHud getInstance() {
        return INSTANCE;
    }

    /**
     * Initialize the HUD system by registering with the event bus.
     * Should be called during plugin setup.
     */
    public void init() {
        PartyEventBus.register(this);
        LOGGER.atInfo().log("PartyPlayerListHud initialized and registered with event bus");
    }

    /**
     * Cleanup when the plugin shuts down.
     */
    public void shutdown() {
        PartyEventBus.unregister(this);
        viewerStates.clear();
        LOGGER.atInfo().log("PartyPlayerListHud shutdown");
    }

    /**
     * Removes all fake members from the HUD for all viewers in a party.
     * Should be called BEFORE party.clearFakeMembers() to get the list of fake member UUIDs.
     *
     * @param party the party whose fake members are being removed
     */
    public void removeFakeMembersFromHud(@Nonnull Party party) {
        Set<UUID> fakeMemberUuids = party.getFakeMembers().keySet();
        if (fakeMemberUuids.isEmpty()) {
            return;
        }

        LOGGER.atInfo().log("[DEBUG] removeFakeMembersFromHud: Removing %d fake members from HUD", fakeMemberUuids.size());

        // For each party member, remove fake members from their HUD
        for (UUID memberUuid : party.getMemberUuids()) {
            ViewerHudState viewerState = viewerStates.get(memberUuid);
            if (viewerState == null) continue;

            // Remove fake member states
            for (UUID fakeUuid : fakeMemberUuids) {
                viewerState.memberStates.remove(fakeUuid);

                // Remove from HUD display
                if (viewerState.hudInstance != null) {
                    try {
                        viewerState.hudInstance.removeMember(fakeUuid);
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log("Error removing fake member from HUD display");
                    }
                }
            }

            // Update HUD visibility (may need to hide if below threshold after removal)
            updateHudVisibility(memberUuid, party);
        }

        LOGGER.atInfo().log("[DEBUG] removeFakeMembersFromHud: Completed");
    }

    // ==================== CONFIGURATION ====================

    /**
     * Sets the minimum member count required to show the HUD.
     *
     * @param min minimum members (1 for debug/solo, 2 for normal)
     */
    public static void setMinMembersForHud(int min) {
        minMembersForHud = Math.max(1, min);
        LOGGER.atInfo().log("HUD minimum members set to %d", minMembersForHud);
    }

    /**
     * Gets the current minimum member count for HUD visibility.
     */
    public static int getMinMembersForHud() {
        return minMembersForHud;
    }

    /**
     * Refreshes HUD visibility and content for a specific player based on their current settings.
     * Call this after player settings have been changed.
     */
    public void refreshHudForPlayer(@Nonnull UUID playerUuid) {
        Party party = PartyMod.getInstance().getPartyManager().getPartyByPlayer(playerUuid);
        updateHudVisibility(playerUuid, party);

        // Also force a content refresh on the HUD instance
        ViewerHudState state = viewerStates.get(playerUuid);
        if (state != null && state.hudInstance != null && state.hudVisible) {
            // Reset member states to force full update on next tick
            for (MemberHudState memberState : state.memberStates.values()) {
                memberState.lastHealth = -1;
                memberState.lastDistance = -1;
            }
            // Trigger immediate pushUpdate
            state.hudInstance.pushUpdate();
        }
    }

    // ==================== EVENT HANDLING ====================

    @Override
    public void onPartyEvent(PartyEvent event) {
        LOGGER.atInfo().log("[DEBUG] onPartyEvent received: %s", event.getClass().getSimpleName());

        if (event instanceof PartyCreateEvent createEvent) {
            handlePartyCreate(createEvent);
        } else if (event instanceof PartyJoinEvent joinEvent) {
            handlePartyJoin(joinEvent);
        } else if (event instanceof PartyLeaveEvent leaveEvent) {
            handlePartyLeave(leaveEvent);
        } else if (event instanceof PartyDisbandEvent disbandEvent) {
            handlePartyDisband(disbandEvent);
        }
    }

    private void handlePartyCreate(@Nonnull PartyCreateEvent event) {
        Party party = event.getParty();
        UUID leaderUuid = event.getLeaderUuid();

        LOGGER.atInfo().log("[DEBUG] handlePartyCreate for leader %s, party members=%d, minForHud=%d",
                leaderUuid, party.getMemberCount(), minMembersForHud);

        // Initialize viewer state for leader
        ViewerHudState state = viewerStates.computeIfAbsent(leaderUuid, ViewerHudState::new);
        state.currentPartyId = party.getId();

        // Check if HUD should be visible
        updateHudVisibility(leaderUuid, party);
    }

    private void handlePartyJoin(@Nonnull PartyJoinEvent event) {
        Party party = event.getParty();
        UUID joiningPlayerUuid = event.getJoiningPlayerUuid();

        LOGGER.atInfo().log("[DEBUG] handlePartyJoin for player %s, party members=%d, minForHud=%d",
                joiningPlayerUuid, party.getMemberCount(), minMembersForHud);

        // Initialize viewer state for joining player
        ViewerHudState joinerState = viewerStates.computeIfAbsent(joiningPlayerUuid, ViewerHudState::new);
        joinerState.currentPartyId = party.getId();

        // Update HUD visibility for all party members
        for (UUID memberUuid : party.getMemberUuids()) {
            updateHudVisibility(memberUuid, party);

            // Add the joining player to other members' HUD states
            if (!memberUuid.equals(joiningPlayerUuid)) {
                ViewerHudState memberState = viewerStates.get(memberUuid);
                if (memberState != null) {
                    memberState.memberStates.computeIfAbsent(joiningPlayerUuid, MemberHudState::new);
                }
            }

            // Add existing member to joiner's HUD state
            if (!memberUuid.equals(joiningPlayerUuid)) {
                joinerState.memberStates.computeIfAbsent(memberUuid, MemberHudState::new);
            }
        }

        // Force immediate UI update for all party members
        forceUpdateForParty(party);
    }

    private void handlePartyLeave(@Nonnull PartyLeaveEvent event) {
        UUID leavingPlayerUuid = event.getLeavingPlayerUuid();
        Party party = event.getParty();

        LOGGER.atInfo().log("Handling party leave for player %s", leavingPlayerUuid);

        // Cleanup HUD state for leaving player
        ViewerHudState leaverState = viewerStates.get(leavingPlayerUuid);
        if (leaverState != null) {
            if (leaverState.hudVisible) {
                try {
                    hideHudForPlayer(leavingPlayerUuid);
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Error hiding HUD for leaving player %s", leavingPlayerUuid);
                }
            }
            // Clear inner maps to prevent memory leak
            leaverState.memberStates.clear();
            if (leaverState.hudInstance != null) {
                leaverState.hudInstance.clearMembers();
            }
        }
        // Now remove the state
        viewerStates.remove(leavingPlayerUuid);

        // Remove leaving player from all remaining members' HUD states
        if (party != null) {
            for (UUID memberUuid : party.getMemberUuids()) {
                ViewerHudState memberState = viewerStates.get(memberUuid);
                if (memberState != null) {
                    memberState.memberStates.remove(leavingPlayerUuid);
                    // Also remove from the HUD display
                    if (memberState.hudInstance != null) {
                        try {
                            memberState.hudInstance.removeMember(leavingPlayerUuid);
                        } catch (Exception e) {
                            LOGGER.atWarning().withCause(e).log("Error removing member from HUD display");
                        }
                    }
                }
                // Update visibility (may need to hide if below threshold)
                updateHudVisibility(memberUuid, party);
            }
        }
    }

    private void handlePartyDisband(@Nonnull PartyDisbandEvent event) {
        LOGGER.atInfo().log("Handling party disband for party %s", event.getPartyId());

        // Hide HUD for all former members
        for (UUID memberUuid : event.getFormerMemberUuids()) {
            ViewerHudState state = viewerStates.get(memberUuid);
            if (state != null) {
                // First hide the HUD if visible
                if (state.hudVisible) {
                    hideHudForPlayer(memberUuid);
                }
                // Clear memberStates to release MemberHudState objects before removing
                // This prevents memory leak when players switch parties frequently
                state.memberStates.clear();
                // Clear HUD display if instance exists
                if (state.hudInstance != null) {
                    state.hudInstance.clearMembers();
                }
            }
            // Remove state AFTER cleanup (hideHudForPlayer needs to access it)
            viewerStates.remove(memberUuid);
        }
    }

    // ==================== HUD VISIBILITY ====================

    private void updateHudVisibility(@Nonnull UUID playerUuid, @Nullable Party party) {
        ViewerHudState state = viewerStates.get(playerUuid);
        if (state == null) {
            return;
        }

        boolean shouldShow = shouldShowHud(party, playerUuid);

        if (shouldShow && !state.hudVisible) {
            LOGGER.atInfo().log("[DEBUG] Will show HUD for player %s", playerUuid);
            showHudForPlayer(playerUuid, party);
            // hudVisible is set inside showHudForPlayer on success
        } else if (!shouldShow && state.hudVisible) {
            LOGGER.atInfo().log("[DEBUG] Will hide HUD for player %s", playerUuid);
            hideHudForPlayer(playerUuid);
            state.hudVisible = false;
        }
    }

    private boolean shouldShowHud(@Nullable Party party, @Nonnull UUID viewerUuid) {
        if (party == null) {
            return false;
        }

        // Check player's HUD visibility setting
        PlayerHudSettings.HudSettings settings = PlayerHudSettings.get(viewerUuid);
        if (!settings.showHud) {
            return false;
        }

        // Count real members + fake members for testing
        int totalCount = party.getMemberCount() + party.getFakeMembers().size();
        return totalCount >= minMembersForHud;
    }

    private void showHudForPlayer(@Nonnull UUID playerUuid, @Nonnull Party party) {
        LOGGER.atInfo().log("[DEBUG] showHudForPlayer START for %s", playerUuid);

        PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
        if (playerRef == null) {
            LOGGER.atInfo().log("[DEBUG] showHudForPlayer: playerRef is null!");
            return;
        }

        Player player = playerRef.getComponent(Player.getComponentType());
        if (player == null) {
            LOGGER.atInfo().log("[DEBUG] showHudForPlayer: Player component is null!");
            return;
        }

        if (player.getWorld() == null) {
            LOGGER.atInfo().log("[DEBUG] showHudForPlayer: Player world is null!");
            return;
        }

        ViewerHudState state = viewerStates.get(playerUuid);
        if (state == null) {
            LOGGER.atInfo().log("[DEBUG] showHudForPlayer: ViewerHudState is null!");
            return;
        }

        // Execute in world context like HungerMod does
        player.getWorld().execute(() -> {
            // Check if we already have a HUD instance - just make it visible
            if (state.hudInstance != null) {
                LOGGER.atInfo().log("[DEBUG] showHudForPlayer: Reusing existing HUD instance for %s", playerRef.getUsername());
                state.hudInstance.setHudVisible(true);
                state.hudVisible = true;
                // Force immediate member data population for reconnected players
                populateMemberDataForViewer(state, playerRef, player, party);
                return;
            }

            // Create new HUD instance
            LOGGER.atInfo().log("[DEBUG] showHudForPlayer: Creating new PartyMemberHud for %s", playerRef.getUsername());
            PartyMemberHud hud = new PartyMemberHud(playerRef);
            state.hudInstance = hud;
            state.hudVisible = true;  // Mark as visible ONLY after successful creation

            // Use MultipleHUD if available for compatibility with other HUD mods
            if (MultipleHudCompat.isAvailable()) {
                LOGGER.atInfo().log("[DEBUG] showHudForPlayer: Using MultipleHUD.setCustomHud...");
                MultipleHudCompat.setCustomHud(player, playerRef, MultipleHudCompat.PARTY_HUD_ID, hud);
            } else {
                LOGGER.atInfo().log("[DEBUG] showHudForPlayer: Using native setCustomHud...");
                player.getHudManager().setCustomHud(playerRef, hud);
            }
            LOGGER.atInfo().log("[DEBUG] showHudForPlayer: setCustomHud DONE, hudVisible=true");

            // Immediately populate member data after HUD is created
            // This ensures HUD shows content right away instead of waiting for next tick
            populateMemberDataForViewer(state, playerRef, player, party);
        });
    }

    private void hideHudForPlayer(@Nonnull UUID playerUuid) {
        PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
        if (playerRef == null) {
            // Still clear state even if player is gone
            ViewerHudState state = viewerStates.get(playerUuid);
            if (state != null) {
                state.hudInstance = null;
                state.hudVisible = false;
            }
            return;
        }

        Player player = playerRef.getComponent(Player.getComponentType());
        if (player == null) {
            ViewerHudState state = viewerStates.get(playerUuid);
            if (state != null) {
                state.hudInstance = null;
                state.hudVisible = false;
            }
            return;
        }

        if (player.getWorld() == null) {
            ViewerHudState state = viewerStates.get(playerUuid);
            if (state != null) {
                state.hudInstance = null;
                state.hudVisible = false;
            }
            return;
        }

        ViewerHudState state = viewerStates.get(playerUuid);

        // Clear state immediately to prevent race conditions
        if (state != null) {
            state.hudVisible = false;
        }

        LOGGER.atInfo().log("Hiding HUD for player %s", playerUuid);

        // Execute cleanup in world context (same as creation)
        player.getWorld().execute(() -> {
            try {
                // Instead of removing the HUD (which crashes), just hide it
                // The HUD stays registered but invisible
                if (state != null && state.hudInstance != null) {
                    state.hudInstance.setHudVisible(false);
                    LOGGER.atInfo().log("[DEBUG] hideHudForPlayer: Called setHudVisible(false) on HUD for %s", playerUuid);
                }
                // Don't call setCustomHud(null) - it causes engine crash!
                // Keep hudInstance reference so we can show() it again later
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("[DEBUG] hideHudForPlayer: Error hiding HUD for %s", playerUuid);
            }
        });
    }

    // ==================== TICK-BASED STAT UPDATES ====================

    @Override
    public void tick(float dt, int index, Store<EntityStore> store) {
        // Cap dt to prevent accumulator spikes after tab-out/tab-in
        float cappedDt = Math.min(dt, STAT_UPDATE_INTERVAL);
        accumulator += cappedDt;
        cleanupAccumulator += cappedDt;

        // Cleanup runs less frequently (every 3 seconds) - expensive operation
        if (cleanupAccumulator >= CLEANUP_INTERVAL) {
            cleanupAccumulator = 0f;
            cleanupOfflinePlayerStates();
        }

        // Stats update runs every second
        if (accumulator >= STAT_UPDATE_INTERVAL) {
            accumulator = 0f;
            // Check for players who are in a party but don't have HUD showing
            // (handles reconnection to existing party)
            checkForMissingHuds();
            updateAllMemberStats(store);
        }
    }

    /**
     * Checks for players who are in a party but don't have their HUD initialized.
     * This handles the case where a player reconnects to an existing party.
     * Also handles the case where a player disconnected and reconnected (stale state).
     */
    private void checkForMissingHuds() {
        PartyManager partyManager = PartyMod.getInstance().getPartyManager();

        // First, clean up stale states for players who went offline
        // This ensures reconnecting players get a fresh HUD
        cleanupOfflinePlayerStates();

        // Iterate through all parties and check their members
        for (Party party : partyManager.getAllParties()) {
            for (UUID playerUuid : party.getMemberUuids()) {
                // Check if player is online
                PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
                if (playerRef == null) continue; // Player offline

                // Player is online and in a party - ensure state exists and HUD is shown
                ViewerHudState state = viewerStates.get(playerUuid);
                if (state == null) {
                    // Player reconnected to existing party - initialize state
                    LOGGER.atInfo().log("[DEBUG] checkForMissingHuds: Player %s is in party but has no state - initializing",
                            playerRef.getUsername());
                    state = new ViewerHudState(playerUuid);
                    state.currentPartyId = party.getId();
                    viewerStates.put(playerUuid, state);
                }

                // Check if HUD should be shown but isn't
                if (!state.hudVisible && shouldShowHud(party, playerUuid)) {
                    LOGGER.atInfo().log("[DEBUG] checkForMissingHuds: Attempting HUD for reconnected player %s",
                            playerRef.getUsername());
                    // Don't set hudVisible here - let showHudForPlayer set it on success
                    // This allows retry on next tick if world wasn't ready
                    showHudForPlayer(playerUuid, party);
                }
            }
        }
    }

    /**
     * Cleans up ViewerHudState for players who have gone offline.
     * This ensures that when they reconnect, they get a fresh HUD state
     * instead of a stale one that still says hudVisible=true.
     */
    private void cleanupOfflinePlayerStates() {
        Iterator<Map.Entry<UUID, ViewerHudState>> iterator = viewerStates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ViewerHudState> entry = iterator.next();
            UUID playerUuid = entry.getKey();

            // Check if player is still online
            PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
            if (playerRef == null) {
                // Player is offline - REMOVE the entry entirely to prevent memory leak
                iterator.remove();
            }
        }
    }

    /**
     * Updates member stats for all viewers using delta detection.
     * Only sends UI updates when stats actually change.
     * Uses currentWorld.getPlayers() to avoid cross-thread access issues.
     */
    @SuppressWarnings("deprecation") // getPlayers() is deprecated but necessary for thread-safety
    private void updateAllMemberStats(Store<EntityStore> store) {
        // Get current world from store - this is the world this ticker is running on
        World currentWorld = store.getExternalData().getWorld();
        if (currentWorld == null) return;

        // Only process viewers who are in THIS world to avoid cross-thread access!
        for (Player viewer : currentWorld.getPlayers()) {
            UUID viewerUuid = viewer.getUuid();
            ViewerHudState viewerState = viewerStates.get(viewerUuid);
            if (viewerState == null || !viewerState.hudVisible) continue;

            try {
                PlayerRef viewerRef = Universe.get().getPlayer(viewerUuid);
                if (viewerRef == null) continue;

                updateMemberStatsForViewer(viewerState, viewerRef, viewer, currentWorld);
            } catch (Exception e) {
                // Ignore errors for individual viewers
            }
        }
    }

    private void updateMemberStatsForViewer(@Nonnull ViewerHudState viewerState,
                                            @Nonnull PlayerRef viewerRef,
                                            @Nonnull Player viewer,
                                            @Nonnull World currentWorld) {
        UUID viewerUuid = viewerState.viewerUuid;

        TransformComponent viewerTransform = viewer.getTransformComponent();
        if (viewerTransform == null) return;

        double viewerX = viewerTransform.getTransform().getPosition().getX();
        double viewerY = viewerTransform.getTransform().getPosition().getY();
        double viewerZ = viewerTransform.getTransform().getPosition().getZ();

        Party party = PartyMod.getInstance().getPartyManager().getPartyByPlayer(viewerUuid);
        if (party == null) return;

        // First update the viewer themselves (always shown first in green slot)
        // Use full stats update to ensure name, distance, and visibility are set
        MemberHudState viewerMemberState = viewerState.memberStates.computeIfAbsent(viewerUuid, MemberHudState::new);
        updateSingleMemberStats(viewerRef, viewerMemberState, viewerX, viewerY, viewerZ, currentWorld);

        // Then check each other party member
        for (UUID memberUuid : party.getMembersExcept(viewerUuid)) {
            MemberHudState memberState = viewerState.memberStates.computeIfAbsent(memberUuid, MemberHudState::new);
            updateSingleMemberStats(viewerRef, memberState, viewerX, viewerY, viewerZ, currentWorld);
        }

        // Also check fake members
        for (FakeMember fakeMember : party.getFakeMembers().values()) {
            MemberHudState memberState = viewerState.memberStates.computeIfAbsent(
                    fakeMember.getUuid(), MemberHudState::new
            );
            updateFakeMemberStats(viewerRef, memberState, fakeMember, viewerX, viewerY, viewerZ);
        }

        // BATCHED UPDATE: Push all member data changes to UI in one operation
        // This reduces pushUpdate() calls from N (one per member) to 1 (one per viewer)
        if (viewerState.hudInstance != null) {
            viewerState.hudInstance.pushUpdate();
        }
    }

    private void updateSingleMemberStats(@Nonnull PlayerRef viewerRef,
                                         @Nonnull MemberHudState memberState,
                                         double viewerX, double viewerY, double viewerZ,
                                         @Nonnull World currentWorld) {
        UUID memberUuid = memberState.memberUuid;
        PlayerRef memberRef = Universe.get().getPlayer(memberUuid);

        boolean online = (memberRef != null);
        String name = online ? memberRef.getUsername() : memberState.lastName;

        float health = 0;
        float maxHealth = 0;
        float stamina = 0;
        float maxStamina = 0;
        int distance = 0;

        if (online) {
            // Get member player - only safe if they're in the same world
            // For cross-world members, we show them as offline (no stats)
            Player memberPlayer = getMemberPlayerSafe(memberRef, currentWorld);
            if (memberPlayer != null) {
                // Get health and stamina from EntityStatMap component
                // This is safe now because we know the player is in the same world
                EntityStatMap stats = getEntityStatMapSafe(memberRef, currentWorld);
                if (stats != null) {
                    int healthIndex = DefaultEntityStatTypes.getHealth();
                    int staminaIndex = DefaultEntityStatTypes.getStamina();

                    EntityStatValue healthStat = stats.get(healthIndex);
                    EntityStatValue staminaStat = stats.get(staminaIndex);

                    if (healthStat != null) {
                        health = healthStat.get();
                        maxHealth = healthStat.getMax();
                    }
                    if (staminaStat != null) {
                        stamina = staminaStat.get();
                        maxStamina = staminaStat.getMax();
                    }
                }

                TransformComponent transform = memberPlayer.getTransformComponent();
                if (transform != null) {
                    double memberX = transform.getTransform().getPosition().getX();
                    double memberY = transform.getTransform().getPosition().getY();
                    double memberZ = transform.getTransform().getPosition().getZ();
                    double dx = memberX - viewerX;
                    double dy = memberY - viewerY;
                    double dz = memberZ - viewerZ;
                    distance = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
                }
            }
        }

        // Check if anything changed
        if (memberState.hasChanged(health, maxHealth, stamina, maxStamina, distance, online, name)) {
            memberState.update(health, maxHealth, stamina, maxStamina, distance, online, name);
            sendMemberStatUpdate(viewerRef, memberUuid, name, health, maxHealth, stamina, maxStamina, distance, online);
        }
    }

    /**
     * Lightweight version: only updates health/stamina bars, no full HUD re-render.
     * Returns true if bars were updated.
     */
    private boolean updateSingleMemberBars(@Nonnull PlayerRef viewerRef,
                                           @Nonnull MemberHudState memberState,
                                           double viewerX, double viewerY, double viewerZ) {
        UUID memberUuid = memberState.memberUuid;
        PlayerRef memberRef = Universe.get().getPlayer(memberUuid);

        if (memberRef == null) return false;

        float health = 0;
        float maxHealth = 0;
        float stamina = 0;
        float maxStamina = 0;

        Player memberPlayer = memberRef.getComponent(Player.getComponentType());
        if (memberPlayer != null) {
            EntityStatMap stats = memberRef.getComponent(
                    EntityStatsModule.get().getEntityStatMapComponentType()
            );
            if (stats != null) {
                int healthIndex = DefaultEntityStatTypes.getHealth();
                int staminaIndex = DefaultEntityStatTypes.getStamina();

                EntityStatValue healthStat = stats.get(healthIndex);
                EntityStatValue staminaStat = stats.get(staminaIndex);

                if (healthStat != null) {
                    health = healthStat.get();
                    maxHealth = healthStat.getMax();
                }
                if (staminaStat != null) {
                    stamina = staminaStat.get();
                    maxStamina = staminaStat.getMax();
                }
            }
        }

        // Check if health/stamina bars changed
        boolean barsChanged = (memberState.lastHealth != health ||
                               memberState.lastMaxHealth != maxHealth ||
                               memberState.lastStamina != stamina ||
                               memberState.lastMaxStamina != maxStamina);

        if (barsChanged) {
            memberState.lastHealth = health;
            memberState.lastMaxHealth = maxHealth;
            memberState.lastStamina = stamina;
            memberState.lastMaxStamina = maxStamina;

            // Update just the bar data (no full pushUpdate)
            ViewerHudState viewerState = viewerStates.get(viewerRef.getUuid());
            if (viewerState != null && viewerState.hudInstance != null) {
                viewerState.hudInstance.updateMemberBars(memberUuid, health, maxHealth, stamina, maxStamina);
            }
        }

        return barsChanged;
    }

    /**
     * Lightweight version for fake members: only updates health/stamina bars.
     * Returns true if bars were updated.
     */
    private boolean updateFakeMemberBars(@Nonnull PlayerRef viewerRef,
                                         @Nonnull MemberHudState memberState,
                                         @Nonnull FakeMember fakeMember,
                                         double viewerX, double viewerY, double viewerZ) {
        // Fake members have fixed stats
        float health = 100f;
        float maxHealth = 100f;
        float stamina = 100f;
        float maxStamina = 100f;

        // Check if health/stamina bars changed
        boolean barsChanged = (memberState.lastHealth != health ||
                               memberState.lastMaxHealth != maxHealth ||
                               memberState.lastStamina != stamina ||
                               memberState.lastMaxStamina != maxStamina);

        if (barsChanged) {
            memberState.lastHealth = health;
            memberState.lastMaxHealth = maxHealth;
            memberState.lastStamina = stamina;
            memberState.lastMaxStamina = maxStamina;

            // Update just the bar data (no full pushUpdate)
            ViewerHudState viewerState = viewerStates.get(viewerRef.getUuid());
            if (viewerState != null && viewerState.hudInstance != null) {
                viewerState.hudInstance.updateMemberBars(fakeMember.getUuid(), health, maxHealth, stamina, maxStamina);
            }
        }

        return barsChanged;
    }

    private void updateFakeMemberStats(@Nonnull PlayerRef viewerRef,
                                       @Nonnull MemberHudState memberState,
                                       @Nonnull FakeMember fakeMember,
                                       double viewerX, double viewerY, double viewerZ) {
        // Fake members are always "online" and have full stats
        String name = fakeMember.getName();
        float health = 100f;
        float maxHealth = 100f;
        float stamina = 100f;
        float maxStamina = 100f;

        double dx = fakeMember.getX() - viewerX;
        double dy = fakeMember.getY() - viewerY;
        double dz = fakeMember.getZ() - viewerZ;
        int distance = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (memberState.hasChanged(health, maxHealth, stamina, maxStamina, distance, true, name)) {
            memberState.update(health, maxHealth, stamina, maxStamina, distance, true, name);
            sendMemberStatUpdate(viewerRef, fakeMember.getUuid(), name, health, maxHealth, stamina, maxStamina, distance, true);
        }
    }

    private void sendMemberStatUpdate(@Nonnull PlayerRef viewerRef,
                                      @Nonnull UUID memberUuid,
                                      @Nonnull String name,
                                      float health, float maxHealth,
                                      float stamina, float maxStamina,
                                      int distance, boolean online) {
        UUID viewerUuid = viewerRef.getUuid();
        ViewerHudState state = viewerStates.get(viewerUuid);

        if (state == null || state.hudInstance == null) {
            return;
        }

        // Use updateMemberData for batched updates (no pushUpdate per member)
        // The caller (updateMemberStatsForViewer) will call pushUpdate() once at the end
        state.hudInstance.updateMemberData(memberUuid, name, health, maxHealth, stamina, maxStamina, distance, online);
    }

    // ==================== IMMEDIATE DATA POPULATION ====================

    /**
     * Immediately populates member data for a viewer's HUD.
     * Called after HUD creation or reconnection to ensure HUD shows content right away.
     * This prevents the 1-second delay that would otherwise occur waiting for the next tick.
     */
    private void populateMemberDataForViewer(@Nonnull ViewerHudState viewerState,
                                              @Nonnull PlayerRef viewerRef,
                                              @Nonnull Player viewer,
                                              @Nonnull Party party) {
        if (viewerState.hudInstance == null) {
            LOGGER.atInfo().log("[DEBUG] populateMemberDataForViewer: hudInstance is null, skipping");
            return;
        }

        UUID viewerUuid = viewerState.viewerUuid;
        World currentWorld = viewer.getWorld();
        if (currentWorld == null) {
            LOGGER.atInfo().log("[DEBUG] populateMemberDataForViewer: world is null, skipping");
            return;
        }

        TransformComponent viewerTransform = viewer.getTransformComponent();
        if (viewerTransform == null) {
            LOGGER.atInfo().log("[DEBUG] populateMemberDataForViewer: transform is null, skipping");
            return;
        }

        double viewerX = viewerTransform.getTransform().getPosition().getX();
        double viewerY = viewerTransform.getTransform().getPosition().getY();
        double viewerZ = viewerTransform.getTransform().getPosition().getZ();

        LOGGER.atInfo().log("[DEBUG] populateMemberDataForViewer: Populating %d members + %d fake members",
                party.getMemberCount(), party.getFakeMembers().size());

        // Populate viewer's own data first
        MemberHudState viewerMemberState = viewerState.memberStates.computeIfAbsent(viewerUuid, MemberHudState::new);
        populateSingleMemberData(viewerRef, viewerMemberState, viewerX, viewerY, viewerZ, currentWorld);

        // Populate other party members
        for (UUID memberUuid : party.getMembersExcept(viewerUuid)) {
            MemberHudState memberState = viewerState.memberStates.computeIfAbsent(memberUuid, MemberHudState::new);
            populateSingleMemberData(viewerRef, memberState, viewerX, viewerY, viewerZ, currentWorld);
        }

        // Populate fake members
        for (FakeMember fakeMember : party.getFakeMembers().values()) {
            MemberHudState memberState = viewerState.memberStates.computeIfAbsent(
                    fakeMember.getUuid(), MemberHudState::new
            );
            populateFakeMemberData(viewerRef, memberState, fakeMember, viewerX, viewerY, viewerZ);
        }

        // Force pushUpdate to send all data to UI immediately
        viewerState.hudInstance.pushUpdate();
        LOGGER.atInfo().log("[DEBUG] populateMemberDataForViewer: Completed immediate HUD population");
    }

    /**
     * Populates data for a single real member immediately (no delta check).
     */
    private void populateSingleMemberData(@Nonnull PlayerRef viewerRef,
                                          @Nonnull MemberHudState memberState,
                                          double viewerX, double viewerY, double viewerZ,
                                          @Nonnull World currentWorld) {
        UUID memberUuid = memberState.memberUuid;
        PlayerRef memberRef = Universe.get().getPlayer(memberUuid);

        boolean online = (memberRef != null);
        String name = online ? memberRef.getUsername() : (memberState.lastName.isEmpty() ? "Offline" : memberState.lastName);

        float health = 0;
        float maxHealth = 0;
        float stamina = 0;
        float maxStamina = 0;
        int distance = 0;

        if (online) {
            Player memberPlayer = getMemberPlayerSafe(memberRef, currentWorld);
            if (memberPlayer != null) {
                EntityStatMap stats = getEntityStatMapSafe(memberRef, currentWorld);
                if (stats != null) {
                    int healthIndex = DefaultEntityStatTypes.getHealth();
                    int staminaIndex = DefaultEntityStatTypes.getStamina();

                    EntityStatValue healthStat = stats.get(healthIndex);
                    EntityStatValue staminaStat = stats.get(staminaIndex);

                    if (healthStat != null) {
                        health = healthStat.get();
                        maxHealth = healthStat.getMax();
                    }
                    if (staminaStat != null) {
                        stamina = staminaStat.get();
                        maxStamina = staminaStat.getMax();
                    }
                }

                TransformComponent transform = memberPlayer.getTransformComponent();
                if (transform != null) {
                    double memberX = transform.getTransform().getPosition().getX();
                    double memberY = transform.getTransform().getPosition().getY();
                    double memberZ = transform.getTransform().getPosition().getZ();
                    double dx = memberX - viewerX;
                    double dy = memberY - viewerY;
                    double dz = memberZ - viewerZ;
                    distance = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
                }
            }
        }

        // Update state and send to HUD (no delta check - we want immediate population)
        memberState.update(health, maxHealth, stamina, maxStamina, distance, online, name);
        sendMemberStatUpdate(viewerRef, memberUuid, name, health, maxHealth, stamina, maxStamina, distance, online);
    }

    /**
     * Populates data for a fake member immediately (no delta check).
     */
    private void populateFakeMemberData(@Nonnull PlayerRef viewerRef,
                                        @Nonnull MemberHudState memberState,
                                        @Nonnull FakeMember fakeMember,
                                        double viewerX, double viewerY, double viewerZ) {
        String name = fakeMember.getName();
        float health = 100f;
        float maxHealth = 100f;
        float stamina = 100f;
        float maxStamina = 100f;

        double dx = fakeMember.getX() - viewerX;
        double dy = fakeMember.getY() - viewerY;
        double dz = fakeMember.getZ() - viewerZ;
        int distance = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Update state and send to HUD (no delta check - we want immediate population)
        memberState.update(health, maxHealth, stamina, maxStamina, distance, true, name);
        sendMemberStatUpdate(viewerRef, fakeMember.getUuid(), name, health, maxHealth, stamina, maxStamina, distance, true);
    }

    // ==================== FORCE UPDATE ====================

    /**
     * Forces an immediate UI update for all members of a party.
     * Used after join/leave events to ensure UI is in sync.
     */
    private void forceUpdateForParty(@Nonnull Party party) {
        for (UUID memberUuid : party.getMemberUuids()) {
            ViewerHudState state = viewerStates.get(memberUuid);
            if (state != null) {
                // Reset all member states to force update on next tick
                for (MemberHudState memberState : state.memberStates.values()) {
                    memberState.lastHealth = -1;
                    memberState.lastMaxHealth = -1;
                    memberState.lastStamina = -1;
                    memberState.lastMaxStamina = -1;
                    memberState.lastDistance = -1;
                }
            }
        }
    }

    // ==================== THREAD-SAFE HELPERS ====================

    /**
     * Safely gets a Player component for a member, only if they're in the same world.
     * Returns null if the member is offline, not loaded, or in a different world.
     *
     * Note: If the member is in a different world, Hytale may log a warning about cross-thread access,
     * but this is unavoidable as we cannot check the world without calling getComponent().
     */
    @SuppressWarnings("deprecation")
    private Player getMemberPlayerSafe(PlayerRef memberRef, World viewerWorld) {
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

    /**
     * Safely gets EntityStatMap for a member, only if they're in the same world.
     * Returns null if the member is offline or in a different world.
     */
    @SuppressWarnings("deprecation")
    private EntityStatMap getEntityStatMapSafe(PlayerRef memberRef, World viewerWorld) {
        // First check if the player is in the same world
        Player memberPlayer = memberRef.getComponent(Player.getComponentType());
        if (memberPlayer == null) {
            return null;
        }

        World memberWorld = memberPlayer.getWorld();
        if (memberWorld == null || !viewerWorld.equals(memberWorld)) {
            return null;
        }

        // Safe to get stats now
        return memberRef.getComponent(EntityStatsModule.get().getEntityStatMapComponentType());
    }
}
