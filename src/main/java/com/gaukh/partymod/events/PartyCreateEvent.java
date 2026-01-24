package com.gaukh.partymod.events;

import com.gaukh.partymod.party.Party;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Event fired when a new party is created.
 * <p>
 * This event indicates that a player has created a new party and become its leader.
 * The HUD should show for this player if the minimum member threshold is met.
 */
public class PartyCreateEvent extends PartyEvent {

    private final Party party;
    private final UUID leaderUuid;

    public PartyCreateEvent(@Nonnull Party party) {
        super();
        this.party = party;
        this.leaderUuid = party.getLeaderUuid();
    }

    @Override
    @Nonnull
    public Party getParty() {
        return party;
    }

    @Override
    @Nonnull
    public String getPartyId() {
        return party.getId();
    }

    @Override
    @Nonnull
    public UUID getPlayerUuid() {
        return leaderUuid;
    }

    /**
     * Gets the UUID of the player who created (and leads) the party.
     */
    @Nonnull
    public UUID getLeaderUuid() {
        return leaderUuid;
    }
}
