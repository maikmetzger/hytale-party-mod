package com.gaukh.partymod.party;

import javax.annotation.Nonnull;

/**
 * Defines how players can join a party.
 * <p>
 * Access types:
 * - OPEN: Anyone can join directly without approval
 * - PASSWORDED: Requires a password to join
 * - REQUEST_ONLY: Players must request to join, leader approves
 * - LOCKED: Only invited players can join (party hidden from public list)
 */
public enum PartyAccessType {
    OPEN(0, "Open", "Anyone can join"),
    PASSWORDED(1, "Password", "Requires password to join"),
    REQUEST_ONLY(2, "Request", "Players must request to join"),
    LOCKED(3, "Locked", "Invite only");

    private final int level;
    private final String displayName;
    private final String description;

    PartyAccessType(int level, String displayName, String description) {
        this.level = level;
        this.displayName = displayName;
        this.description = description;
    }

    public int getLevel() {
        return level;
    }

    @Nonnull
    public String getDisplayName() {
        return displayName;
    }

    @Nonnull
    public String getDescription() {
        return description;
    }

    /**
     * Whether this party should appear in the public parties list.
     * LOCKED parties are hidden.
     */
    public boolean isPubliclyVisible() {
        return this != LOCKED;
    }

    @Nonnull
    public static PartyAccessType fromLevel(int level) {
        for (PartyAccessType type : values()) {
            if (type.level == level) {
                return type;
            }
        }
        return LOCKED;
    }
}
