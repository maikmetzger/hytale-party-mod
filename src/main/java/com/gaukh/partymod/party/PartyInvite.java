package com.gaukh.partymod.party;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;

/**
 * Data model representing a pending party invitation.
 * <p>
 * This class holds invitation state:
 * - Inviter UUID (who sent the invite)
 * - Invitee UUID (who received the invite)
 * - Party ID (which party they're invited to)
 * - Expiration timestamp (invites expire after timeout)
 * <p>
 * Invitations are stored in PartyManager and cleaned up when accepted,
 * declined, or expired.
 *
 * @see PartyManager for invitation logic (send, accept, decline)
 * @see Party for the party data model
 */
public class PartyInvite {

    private final UUID inviterUuid;
    private final UUID inviteeUuid;
    private final String partyId;
    private final long createdAt;
    private final long expiresAt;

    public PartyInvite(@Nonnull UUID inviterUuid, @Nonnull UUID inviteeUuid,
                       @Nonnull String partyId, int timeoutSeconds) {
        this.inviterUuid = inviterUuid;
        this.inviteeUuid = inviteeUuid;
        this.partyId = partyId;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = createdAt + (timeoutSeconds * 1000L);
    }

    @Nonnull
    public UUID getInviterUuid() {
        return inviterUuid;
    }

    @Nonnull
    public UUID getInviteeUuid() {
        return inviteeUuid;
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
    public boolean equals(Object object) {
        if (this == object) return true;

        if (object == null || getClass() != object.getClass()) return false;

        PartyInvite that = (PartyInvite) object;

        return Objects.equals(inviterUuid, that.inviterUuid) &&
               Objects.equals(inviteeUuid, that.inviteeUuid) &&
               Objects.equals(partyId, that.partyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inviterUuid, inviteeUuid, partyId);
    }

    @Override
    public String toString() {
        return "PartyInvite{" +
                "inviter=" + inviterUuid +
                ", invitee=" + inviteeUuid +
                ", partyId='" + partyId + '\'' +
                ", expired=" + isExpired() +
                '}';
    }
}
