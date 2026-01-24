package com.gaukh.partymod.events;

import com.gaukh.partymod.party.Party;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;

/**
 * Event fired when a party is disbanded.
 * <p>
 * This event is fired after the party has been removed from the system.
 * All former members' HUDs should be hidden.
 * <p>
 * Note: The party reference is null after disband, but member UUIDs
 * are preserved so listeners can clean up their state.
 */
public class PartyDisbandEvent extends PartyEvent {

    private final String partyId;
    private final UUID leaderUuid;
    private final Set<UUID> formerMemberUuids;

    /**
     * Creates a disband event.
     *
     * @param partyId the ID of the disbanded party
     * @param leaderUuid the UUID of the former party leader
     * @param formerMemberUuids the UUIDs of all former party members (including leader)
     */
    public PartyDisbandEvent(@Nonnull String partyId, @Nonnull UUID leaderUuid,
                             @Nonnull Set<UUID> formerMemberUuids) {
        super();
        this.partyId = partyId;
        this.leaderUuid = leaderUuid;
        this.formerMemberUuids = Set.copyOf(formerMemberUuids);
    }

    @Override
    @Nullable
    public Party getParty() {
        return null; // Party no longer exists
    }

    @Override
    @Nonnull
    public String getPartyId() {
        return partyId;
    }

    @Override
    @Nonnull
    public UUID getPlayerUuid() {
        return leaderUuid;
    }

    /**
     * Gets the UUID of the former party leader.
     */
    @Nonnull
    public UUID getLeaderUuid() {
        return leaderUuid;
    }

    /**
     * Gets the UUIDs of all former party members (including the leader).
     * This is useful for cleaning up HUD state for all affected players.
     */
    @Nonnull
    public Set<UUID> getFormerMemberUuids() {
        return formerMemberUuids;
    }
}
