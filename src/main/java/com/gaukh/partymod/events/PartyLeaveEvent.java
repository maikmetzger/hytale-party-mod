package com.gaukh.partymod.events;

import com.gaukh.partymod.party.Party;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Event fired when a player leaves a party.
 * <p>
 * This event is fired after the player has been removed from the party.
 * The leaving player's HUD should be hidden, and other members' HUDs
 * should be updated to remove the leaving player.
 */
public class PartyLeaveEvent extends PartyEvent {

    private final Party party;
    private final String partyId;
    private final UUID leavingPlayerUuid;
    private final boolean wasKicked;

    /**
     * Creates a leave event.
     *
     * @param party the party (may be null if disbanded)
     * @param partyId the party ID (always available)
     * @param leavingPlayerUuid the UUID of the leaving player
     * @param wasKicked true if the player was kicked, false if they left voluntarily
     */
    public PartyLeaveEvent(@Nullable Party party, @Nonnull String partyId,
                           @Nonnull UUID leavingPlayerUuid, boolean wasKicked) {
        super();
        this.party = party;
        this.partyId = partyId;
        this.leavingPlayerUuid = leavingPlayerUuid;
        this.wasKicked = wasKicked;
    }

    @Override
    @Nullable
    public Party getParty() {
        return party;
    }

    @Override
    @Nonnull
    public String getPartyId() {
        return partyId;
    }

    @Override
    @Nonnull
    public UUID getPlayerUuid() {
        return leavingPlayerUuid;
    }

    /**
     * Gets the UUID of the player who left the party.
     */
    @Nonnull
    public UUID getLeavingPlayerUuid() {
        return leavingPlayerUuid;
    }

    /**
     * Returns true if the player was kicked from the party.
     */
    public boolean wasKicked() {
        return wasKicked;
    }
}
