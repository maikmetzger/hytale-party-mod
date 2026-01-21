package com.gaukh.partymod.events;

/**
 * Interface for components that want to receive party events.
 * <p>
 * Implementations should register with {@link PartyEventBus#register(PartyEventListener)}
 * to start receiving events.
 *
 * @see PartyEvent for the base event class
 * @see PartyEventBus for the event dispatcher
 */
@FunctionalInterface
public interface PartyEventListener {

    /**
     * Called when a party event occurs.
     * Implementations should check the event type and handle accordingly.
     *
     * @param event the party event
     */
    void onPartyEvent(PartyEvent event);
}
