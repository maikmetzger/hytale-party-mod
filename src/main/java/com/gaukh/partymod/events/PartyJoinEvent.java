package com.gaukh.partymod.events;

import com.gaukh.partymod.party.Party;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Event fired when a player joins a party.
 * <p>
 * This event is fired after the player has been added to the party.
 * All party members' HUDs should be updated to show the new member.
 */
public class PartyJoinEvent extends PartyEvent {

    private final Party party;
    private final UUID joiningPlayerUuid;

    public PartyJoinEvent(@Nonnull Party party, @Nonnull UUID joiningPlayerUuid) {
        super();
        this.party = party;
        this.joiningPlayerUuid = joiningPlayerUuid;
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
        return joiningPlayerUuid;
    }

    /**
     * Gets the UUID of the player who joined the party.
     */
    @Nonnull
    public UUID getJoiningPlayerUuid() {
        return joiningPlayerUuid;
    }
}
