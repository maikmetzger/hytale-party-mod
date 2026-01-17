package com.gaukh.partymod.party;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Represents a pending join request for REQUEST_ONLY parties.
 * <p>
 * When a player wants to join a party with access type REQUEST_ONLY,
 * a join request is created. The party leader can then accept or decline.
 * Requests expire after a timeout (default 5 minutes).
 */
public class PartyJoinRequest {

    private static final int DEFAULT_TIMEOUT_SECONDS = 300; // 5 minutes

    private final UUID requesterUuid;
    private final String partyId;
    private final long createdAt;
    private final long expiresAt;

    public PartyJoinRequest(@Nonnull UUID requesterUuid, @Nonnull String partyId) {
        this(requesterUuid, partyId, DEFAULT_TIMEOUT_SECONDS);
    }

    public PartyJoinRequest(@Nonnull UUID requesterUuid, @Nonnull String partyId, int timeoutSeconds) {
        this.requesterUuid = requesterUuid;
        this.partyId = partyId;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = createdAt + (timeoutSeconds * 1000L);
    }

    public PartyJoinRequest(@Nonnull UUID requesterUuid, @Nonnull String partyId, long createdAt, long expiresAt) {
        this.requesterUuid = requesterUuid;
        this.partyId = partyId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    @Nonnull
    public UUID getRequesterUuid() {
        return requesterUuid;
    }

    @Nonnull
    public String getPartyId() {
        return partyId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    public long getRemainingTimeMs() {
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }

    @Override
    public String toString() {
        return "PartyJoinRequest{" +
                "requester=" + requesterUuid +
                ", partyId='" + partyId + '\'' +
                ", expired=" + isExpired() +
                '}';
    }
}
