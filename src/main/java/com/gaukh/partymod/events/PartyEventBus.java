package com.gaukh.partymod.events;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Simple event dispatcher for party events.
 * <p>
 * Uses a copy-on-write list for thread safety, allowing listeners to be
 * registered and unregistered at any time without causing issues during
 * event dispatch.
 * <p>
 * Usage:
 * <pre>
 * // Register a listener
 * PartyEventBus.register(event -> {
 *     if (event instanceof PartyCreateEvent) {
 *         // Handle party creation
 *     }
 * });
 *
 * // Fire an event
 * PartyEventBus.fire(new PartyCreateEvent(party));
 * </pre>
 *
 * @see PartyEvent for the base event class
 * @see PartyEventListener for the listener interface
 */
public final class PartyEventBus {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // CopyOnWriteArrayList is thread-safe and allows iteration during modification
    private static final List<PartyEventListener> listeners = new CopyOnWriteArrayList<>();

    private PartyEventBus() {
        // Prevent instantiation
    }

    /**
     * Registers a listener to receive party events.
     *
     * @param listener the listener to register
     */
    public static void register(@Nonnull PartyEventListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            LOGGER.atFine().log("Registered party event listener: %s", listener.getClass().getSimpleName());
        }
    }

    /**
     * Unregisters a listener from receiving party events.
     *
     * @param listener the listener to unregister
     */
    public static void unregister(@Nonnull PartyEventListener listener) {
        if (listeners.remove(listener)) {
            LOGGER.atFine().log("Unregistered party event listener: %s", listener.getClass().getSimpleName());
        }
    }

    /**
     * Fires an event to all registered listeners.
     * Exceptions in individual listeners are caught and logged to prevent
     * one faulty listener from breaking others.
     *
     * @param event the event to fire
     */
    public static void fire(@Nonnull PartyEvent event) {
        LOGGER.atFine().log("Firing party event: %s", event.getClass().getSimpleName());

        for (PartyEventListener listener : listeners) {
            try {
                listener.onPartyEvent(event);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log(
                        "Exception in party event listener %s while handling %s",
                        listener.getClass().getSimpleName(),
                        event.getClass().getSimpleName()
                );
            }
        }
    }

    /**
     * Gets the number of registered listeners.
     * Useful for debugging.
     */
    public static int getListenerCount() {
        return listeners.size();
    }

    /**
     * Clears all registered listeners.
     * Should only be used during shutdown or testing.
     */
    public static void clearListeners() {
        listeners.clear();
        LOGGER.atFine().log("Cleared all party event listeners");
    }
}
