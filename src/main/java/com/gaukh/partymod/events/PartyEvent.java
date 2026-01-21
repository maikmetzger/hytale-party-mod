package com.gaukh.partymod.events;

import com.gaukh.partymod.party.Party;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Base class for all party-related events.
 * <p>
 * The event system enables reactive updates - components like the HUD
 * only update when something actually changes, rather than constantly polling.
 *
 * @see PartyEventBus for dispatching events
 * @see PartyEventListener for receiving events
 */
public abstract class PartyEvent {

    private final long timestamp;

    protected PartyEvent() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Gets the party associated with this event, if any.
     * Some events (like disband) may return null after the party is removed.
     */
    @Nullable
    public abstract Party getParty();

    /**
     * Gets the party ID. Always available, even after disband.
     */
    @Nonnull
    public abstract String getPartyId();

    /**
     * Gets the primary player UUID associated with this event.
     * For create events, this is the leader.
     * For join/leave events, this is the joining/leaving player.
     */
    @Nonnull
    public abstract UUID getPlayerUuid();

    /**
     * Gets the timestamp when this event was created.
     */
    public long getTimestamp() {
        return timestamp;
    }
}
