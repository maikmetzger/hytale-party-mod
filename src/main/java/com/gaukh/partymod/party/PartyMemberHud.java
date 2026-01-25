package com.gaukh.partymod.party;

import com.gaukh.partymod.config.PlayerHudSettings;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom HUD element displaying party member information.
 * <p>
 * This is the actual HUD that gets registered with the HudManager.
 * It displays a list of party members with their health, stamina, and distance.
 * <p>
 * PERFORMANCE NOTES:
 * - Uses fresh UICommandBuilder per update to prevent command accumulation
 * - Uses update(false, builder) to preserve UI structure while updating values
 * - Thread-safe via synchronized blocks on critical sections
 * - Cached Comparator to avoid allocation per sort
 */
public class PartyMemberHud extends CustomUIHud {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final int MAX_DISPLAYED_MEMBERS = 8;

    // Pre-cached UI selectors to avoid String concatenation in hot loops
    private static final String[] NAME_SELECTORS = new String[MAX_DISPLAYED_MEMBERS];
    private static final String[] DISTANCE_SELECTORS = new String[MAX_DISPLAYED_MEMBERS];
    private static final String[] HEALTH_SELECTORS = new String[MAX_DISPLAYED_MEMBERS];
    private static final String[] STAMINA_SELECTORS = new String[MAX_DISPLAYED_MEMBERS];
    private static final String[] VISIBLE_SELECTORS = new String[MAX_DISPLAYED_MEMBERS];

    static {
        for (int i = 0; i < MAX_DISPLAYED_MEMBERS; i++) {
            NAME_SELECTORS[i] = "#Member" + i + "Name.Text";
            DISTANCE_SELECTORS[i] = "#Member" + i + "Distance.Text";
            HEALTH_SELECTORS[i] = "#Member" + i + "Health.Value";
            STAMINA_SELECTORS[i] = "#Member" + i + "Stamina.Value";
            VISIBLE_SELECTORS[i] = "#Member" + i + ".Visible";
        }
    }

    // Pre-cached distance strings for common distances (0-999m)
    private static final String[] DISTANCE_STRINGS = new String[1000];
    static {
        for (int i = 0; i < 1000; i++) {
            DISTANCE_STRINGS[i] = "(" + i + "m)";
        }
    }

    // Flag to track if HUD has been initialized (build() was called)
    // We no longer store the builder - instead create fresh ones per update
    private volatile boolean hudInitialized = false;

    // Store viewer UUID for settings lookup
    private final UUID viewerUuid;

    // Track member data for display (thread-safe map)
    private final Map<UUID, MemberDisplayData> memberData = new ConcurrentHashMap<>();

    // Track member order for consistent display (synchronized for thread-safety)
    private final Set<UUID> memberOrder = Collections.synchronizedSet(new LinkedHashSet<>());

    // Flag to track if an update is pending (data was added before build() was called)
    private volatile boolean pendingUpdate = false;

    // Reusable lists - protected by synchronized(this) in methods that use them
    private final List<UUID> reusableMembersToShow = new ArrayList<>(MAX_DISPLAYED_MEMBERS);
    private final List<UUID> reusableOnlineMembers = new ArrayList<>(MAX_DISPLAYED_MEMBERS);

    // Cached Comparator for distance sorting - avoids creating new Comparator per pushUpdate()
    // The lambda captures 'memberData' reference (not contents), so it always reads fresh data
    private final Comparator<UUID> distanceComparator = Comparator.comparingInt(uuid -> {
        MemberDisplayData data = memberData.get(uuid);
        return data != null ? data.distance : Integer.MAX_VALUE;
    });

    /**
     * Data for displaying a single party member.
     */
    public static class MemberDisplayData {
        public volatile String name;
        public volatile float health;
        public volatile float maxHealth;
        public volatile float stamina;
        public volatile float maxStamina;
        public volatile int distance;
        public volatile boolean online;

        public MemberDisplayData(String name) {
            this.name = name;
            this.health = 100f;
            this.maxHealth = 100f;
            this.stamina = 100f;
            this.maxStamina = 100f;
            this.distance = 0;
            this.online = true;
        }

        public float getHealthPercent() {
            float max = maxHealth;
            return max > 0 ? health / max : 0;
        }

        public float getStaminaPercent() {
            float max = maxStamina;
            return max > 0 ? stamina / max : 0;
        }
    }

    public PartyMemberHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
        this.viewerUuid = playerRef.getUuid();
        LOGGER.atInfo().log("PartyMemberHud created for player %s", playerRef.getUsername());
    }

    @Override
    protected void build(@Nonnull UICommandBuilder builder) {
        LOGGER.atInfo().log("PartyMemberHud build() called, memberData size=%d", memberData.size());

        // Load the party HUD UI file - this establishes the UI structure
        builder.append("Hud/Party/PartyHud.ui");

        // Mark HUD as initialized
        hudInitialized = true;

        // If there were updates queued before build() was called, process them now
        if (pendingUpdate || !memberData.isEmpty()) {
            pendingUpdate = false;
            pushUpdate();
        }

        LOGGER.atInfo().log("PartyMemberHud build() complete");
    }

    /**
     * Updates the display data for a member WITHOUT pushing to the UI.
     * Use this for batched updates - call pushUpdate() once after all members are updated.
     */
    public void updateMemberData(@Nonnull UUID memberUuid, @Nonnull String name,
                                  float health, float maxHealth,
                                  float stamina, float maxStamina,
                                  int distance, boolean online) {
        // Track member order - Set.add() returns true if element was new
        memberOrder.add(memberUuid);

        MemberDisplayData data = memberData.computeIfAbsent(memberUuid, k -> new MemberDisplayData(name));
        data.name = name;
        data.health = health;
        data.maxHealth = maxHealth;
        data.stamina = stamina;
        data.maxStamina = maxStamina;
        data.distance = distance;
        data.online = online;
    }

    /**
     * Removes a member from the display.
     */
    public void removeMember(@Nonnull UUID memberUuid) {
        memberOrder.remove(memberUuid);
        if (memberData.remove(memberUuid) != null) {
            pushUpdate();
        }
    }

    /**
     * Clears all members from the display.
     */
    public void clearMembers() {
        memberOrder.clear();
        memberData.clear();
        pushUpdate();
    }

    /**
     * Hides the entire HUD by setting the root container visibility to false.
     * Uses a fresh UICommandBuilder to prevent command accumulation.
     */
    public void setHudVisible(boolean visible) {
        if (!hudInitialized) {
            pendingUpdate = true;
            return;
        }

        // Create fresh builder to prevent command accumulation
        UICommandBuilder freshBuilder = new UICommandBuilder();
        freshBuilder.set("#PartyHudRoot.Visible", visible);
        freshBuilder.set("#MemberListContainer.Visible", visible);

        // update(false, ...) preserves UI structure, only updates values
        update(false, freshBuilder);
    }

    /**
     * Gets the number of members currently displayed.
     */
    public int getMemberCount() {
        return memberData.size();
    }

    /**
     * Lightweight update: only updates health and stamina bar values.
     * Uses a fresh UICommandBuilder to prevent command accumulation.
     */
    public void pushBarsOnly() {
        if (!hudInitialized) {
            return;
        }

        // Create fresh builder to prevent command accumulation
        UICommandBuilder freshBuilder = new UICommandBuilder();

        // Synchronized snapshot of memberOrder to prevent ConcurrentModificationException
        List<UUID> snapshot;
        synchronized (memberOrder) {
            snapshot = new ArrayList<>(memberOrder);
        }

        int visibleCount = Math.min(snapshot.size(), MAX_DISPLAYED_MEMBERS);
        for (int i = 0; i < visibleCount; i++) {
            UUID memberUuid = snapshot.get(i);
            MemberDisplayData data = memberData.get(memberUuid);
            if (data != null) {
                freshBuilder.set(HEALTH_SELECTORS[i], data.getHealthPercent());
                freshBuilder.set(STAMINA_SELECTORS[i], data.getStaminaPercent());
            }
        }

        // update(false, ...) preserves UI structure, only updates values
        update(false, freshBuilder);
    }

    /**
     * Updates just the health/stamina data for a member without pushing to UI.
     */
    public void updateMemberBars(@Nonnull UUID memberUuid, float health, float maxHealth,
                                  float stamina, float maxStamina) {
        MemberDisplayData data = memberData.get(memberUuid);
        if (data != null) {
            data.health = health;
            data.maxHealth = maxHealth;
            data.stamina = stamina;
            data.maxStamina = maxStamina;
        }
    }

    /**
     * Pushes the current state to the UI.
     * Creates a fresh UICommandBuilder each time to prevent command accumulation.
     * Uses update(false, builder) to preserve UI structure while updating values.
     */
    public void pushUpdate() {
        if (!hudInitialized) {
            pendingUpdate = true;
            return;
        }

        // Load player settings
        PlayerHudSettings.HudSettings settings = PlayerHudSettings.get(viewerUuid);

        // Synchronized block to protect reusable lists from concurrent access
        synchronized (this) {
            // Create synchronized snapshot of memberOrder
            reusableMembersToShow.clear();
            synchronized (memberOrder) {
                reusableMembersToShow.addAll(memberOrder);
            }

            // Filter: remove self if showSelf is disabled
            if (!settings.showSelf) {
                reusableMembersToShow.remove(viewerUuid);
            }

            // Sort by distance if orderMode is DISTANCE (uses cached Comparator)
            if (settings.orderMode == PlayerHudSettings.OrderMode.DISTANCE) {
                reusableMembersToShow.sort(distanceComparator);
            }

            // Apply maxDisplayedMembers limit
            int limit = Math.min(settings.maxDisplayedMembers, MAX_DISPLAYED_MEMBERS);

            // Filter out offline members
            reusableOnlineMembers.clear();
            for (UUID uuid : reusableMembersToShow) {
                MemberDisplayData data = memberData.get(uuid);
                if (data != null && data.online) {
                    reusableOnlineMembers.add(uuid);
                }
            }

            // Create fresh builder to prevent command accumulation
            // This is the KEY FIX - each update gets a new builder
            UICommandBuilder freshBuilder = new UICommandBuilder();

            // Update each member slot
            for (int i = 0; i < MAX_DISPLAYED_MEMBERS; i++) {
                if (i < reusableOnlineMembers.size() && i < limit) {
                    UUID memberUuid = reusableOnlineMembers.get(i);
                    MemberDisplayData data = memberData.get(memberUuid);
                    if (data != null) {
                        freshBuilder.set(NAME_SELECTORS[i], data.name);

                        // Use cached distance string if in range
                        String distanceStr = (data.distance >= 0 && data.distance < DISTANCE_STRINGS.length)
                                ? DISTANCE_STRINGS[data.distance]
                                : "(" + data.distance + "m)";
                        freshBuilder.set(DISTANCE_SELECTORS[i], distanceStr);

                        freshBuilder.set(HEALTH_SELECTORS[i], data.getHealthPercent());
                        freshBuilder.set(STAMINA_SELECTORS[i], data.getStaminaPercent());
                        freshBuilder.set(VISIBLE_SELECTORS[i], true);
                    } else {
                        freshBuilder.set(VISIBLE_SELECTORS[i], false);
                    }
                } else {
                    freshBuilder.set(VISIBLE_SELECTORS[i], false);
                }
            }

            // update(false, ...) preserves UI structure, only updates values
            // This is safe because the UI was already loaded in build() via append()
            update(false, freshBuilder);
        }
    }
}
