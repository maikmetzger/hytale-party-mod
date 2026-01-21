package com.gaukh.partymod.party;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom HUD element displaying party member information.
 * <p>
 * This is the actual HUD that gets registered with the HudManager.
 * It displays a list of party members with their health, stamina, and distance.
 */
public class PartyMemberHud extends CustomUIHud {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final int MAX_DISPLAYED_MEMBERS = 8;

    // Store builder reference for updates (like HealPreviewHUD pattern)
    private UICommandBuilder builder;

    // Track member data for display
    private final Map<UUID, MemberDisplayData> memberData = new ConcurrentHashMap<>();

    // Track member order for consistent display
    private final List<UUID> memberOrder = new ArrayList<>();

    /**
     * Data for displaying a single party member.
     */
    public static class MemberDisplayData {
        public String name;
        public float health;
        public float maxHealth;
        public float stamina;
        public float maxStamina;
        public int distance;
        public boolean online;

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
            return maxHealth > 0 ? health / maxHealth : 0;
        }

        public float getStaminaPercent() {
            return maxStamina > 0 ? stamina / maxStamina : 0;
        }
    }

    public PartyMemberHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
        LOGGER.atInfo().log("[DEBUG] PartyMemberHud CONSTRUCTOR called for player %s", playerRef.getUsername());
    }

    @Override
    protected void build(@Nonnull UICommandBuilder builder) {
        LOGGER.atInfo().log("[DEBUG] PartyMemberHud BUILD called, memberData size=%d", memberData.size());

        // Store builder reference for later updates (like HealPreviewHUD pattern)
        this.builder = builder;

        // Load the party HUD UI file from HUD/Party/ folder
        builder.append("HUD/Party/PartyHud.ui");
        LOGGER.atInfo().log("[DEBUG] Appended HUD/Party/PartyHud.ui to builder");

        // For now, just log that HUD was loaded - test basic visibility first
        // TODO: Re-enable member visibility once basic HUD works
        LOGGER.atInfo().log("[DEBUG] HUD loaded successfully, memberData size=%d", memberData.size());
    }

    /**
     * Sets data for a single member slot using simple selector syntax.
     * Uses #ElementId.Property format like HealPreviewHUD.
     */
    private void setMemberData(int index, MemberDisplayData data, boolean visible) {
        // Simple selector: #Member0.Visible (not nested)
        builder.set("#Member" + index + ".Visible", visible);

        if (visible && data != null) {
            // For nested elements, we need unique IDs in the UI file
            // For now, just set visibility - we'll update UI file structure if needed
            LOGGER.atFine().log("Setting member %d: %s, visible=%s", index, data.name, visible);
        }
    }

    /**
     * Updates the display data for a member and pushes changes to the UI.
     */
    public void updateMember(@Nonnull UUID memberUuid, @Nonnull String name,
                             float health, float maxHealth,
                             float stamina, float maxStamina,
                             int distance, boolean online) {
        LOGGER.atFine().log("[DEBUG] updateMember called: %s health=%.0f/%.0f dist=%dm", name, health, maxHealth, distance);

        // Track member order - add to list if new
        if (!memberOrder.contains(memberUuid)) {
            memberOrder.add(memberUuid);
            LOGGER.atInfo().log("[DEBUG] Added new member to HUD: %s (total: %d)", name, memberOrder.size());
        }

        MemberDisplayData data = memberData.computeIfAbsent(memberUuid, k -> new MemberDisplayData(name));
        data.name = name;
        data.health = health;
        data.maxHealth = maxHealth;
        data.stamina = stamina;
        data.maxStamina = maxStamina;
        data.distance = distance;
        data.online = online;

        // Push update to the UI
        pushUpdate();
    }

    /**
     * Removes a member from the display.
     */
    public void removeMember(@Nonnull UUID memberUuid) {
        memberOrder.remove(memberUuid);
        if (memberData.remove(memberUuid) != null) {
            LOGGER.atInfo().log("[DEBUG] Removed member from HUD (remaining: %d)", memberOrder.size());
            pushUpdate();
        }
    }

    /**
     * Clears all members from the display.
     */
    public void clearMembers() {
        memberOrder.clear();
        memberData.clear();
        LOGGER.atInfo().log("[DEBUG] Cleared all members from HUD");
        pushUpdate();
    }

    /**
     * Hides the entire HUD by setting the root container visibility to false.
     * This is safer than removing the HUD which can cause engine crashes.
     * Named setHudVisible to avoid conflict with engine's show()/hide() methods.
     */
    public void setHudVisible(boolean visible) {
        LOGGER.atInfo().log("[DEBUG] PartyMemberHud.setHudVisible(%s) called", visible);
        if (builder == null) {
            LOGGER.atWarning().log("[DEBUG] setHudVisible: builder is null, skipping");
            return;
        }
        // Set visibility on both root and container
        builder.set("#PartyHudRoot.Visible", visible);
        builder.set("#MemberListContainer.Visible", visible);
        // Use update(true, builder) to force a full UI refresh
        update(true, builder);
        LOGGER.atInfo().log("[DEBUG] PartyMemberHud.setHudVisible - set Visible=%s (full update)", visible);
    }

    /**
     * Gets the number of members currently displayed.
     */
    public int getMemberCount() {
        return memberData.size();
    }

    /**
     * Lightweight update: only updates health and stamina bar values for visible members.
     * Does NOT update name, distance, or visibility - much faster than full pushUpdate().
     * Call this for periodic stat refreshes.
     */
    public void pushBarsOnly() {
        if (builder == null) {
            return;
        }

        int visibleCount = Math.min(memberOrder.size(), MAX_DISPLAYED_MEMBERS);
        for (int i = 0; i < visibleCount; i++) {
            UUID memberUuid = memberOrder.get(i);
            MemberDisplayData data = memberData.get(memberUuid);
            if (data != null) {
                builder.set("#Member" + i + "Health.Value", data.getHealthPercent());
                builder.set("#Member" + i + "Stamina.Value", data.getStaminaPercent());
            }
        }

        update(true, builder);
        LOGGER.atFine().log("[DEBUG] pushBarsOnly: updated health/stamina for %d members", visibleCount);
    }

    /**
     * Updates just the health/stamina data for a member without pushing to UI.
     * Call pushBarsOnly() afterwards to send the update.
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
     * Uses the stored builder reference like HealPreviewHUD pattern.
     */
    private void pushUpdate() {
        if (builder == null) {
            LOGGER.atWarning().log("[DEBUG] pushUpdate: builder is null, skipping");
            return;
        }

        LOGGER.atFine().log("[DEBUG] pushUpdate called, memberOrder size=%d", memberOrder.size());

        // Update each member slot
        for (int i = 0; i < MAX_DISPLAYED_MEMBERS; i++) {
            if (i < memberOrder.size()) {
                UUID memberUuid = memberOrder.get(i);
                MemberDisplayData data = memberData.get(memberUuid);
                if (data != null) {
                    // Set the name text
                    builder.set("#Member" + i + "Name.Text", data.name);

                    // Set the distance text (format: "(123m)")
                    builder.set("#Member" + i + "Distance.Text", "(" + data.distance + "m)");

                    // Set the health bar value (0.0 to 1.0)
                    builder.set("#Member" + i + "Health.Value", data.getHealthPercent());

                    // Set the stamina bar value (0.0 to 1.0)
                    builder.set("#Member" + i + "Stamina.Value", data.getStaminaPercent());

                    // Show the entire member slot
                    builder.set("#Member" + i + ".Visible", true);
                } else {
                    // No data - hide slot
                    builder.set("#Member" + i + ".Visible", false);
                }
            } else {
                // No member in this slot - hide it
                builder.set("#Member" + i + ".Visible", false);
            }
        }

        // Send update to client
        update(true, builder);
        LOGGER.atFine().log("[DEBUG] pushUpdate: sent %d members to UI (name, distance, health, stamina)", Math.min(memberOrder.size(), MAX_DISPLAYED_MEMBERS));
    }
}
