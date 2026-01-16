package com.gaukh.partymod.party;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service class that manages all party operations.
 * <p>
 * This is the central controller for the party system. It maintains:
 * - All active parties (Map: partyId -> Party)
 * - Player-to-party mapping (Map: playerUuid -> partyId)
 * - Pending invitations (Map: inviteeUuid -> PartyInvite)
 * <p>
 * Provides all business logic:
 * - createParty / disbandParty
 * - sendInvite / acceptInvite / declineInvite
 * - leaveParty / kickPlayer
 * - broadcastToParty
 *
 * @see Party for party data model
 * @see PartyInvite for invitation data model
 */
public class PartyManager {

    private final Map<String, Party> parties = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerPartyMap = new ConcurrentHashMap<>();
    private final Map<UUID, PartyInvite> pendingInvites = new ConcurrentHashMap<>();

    @Nullable
    public Party createParty(@Nonnull UUID leaderUuid) {
        if (isInParty(leaderUuid)) {
            return null;
        }

        Party party = new Party(leaderUuid);
        parties.put(party.getId(), party);
        playerPartyMap.put(leaderUuid, party.getId());
        return party;
    }

    public void disbandParty(@Nonnull String partyId) {
        Party party = parties.remove(partyId);
        if (party == null) return;

        for (UUID memberUuid : party.getMemberUuids()) {
            playerPartyMap.remove(memberUuid);
        }
        broadcastToParty(party, Message.raw("Party has been disbanded."));
    }

    @Nullable
    public Party getPartyByPlayer(@Nonnull UUID playerUuid) {
        String partyId = playerPartyMap.get(playerUuid);
        return partyId != null ? parties.get(partyId) : null;
    }

    public boolean isInParty(@Nonnull UUID playerUuid) {
        return playerPartyMap.containsKey(playerUuid);
    }

    public boolean sendInvite(@Nonnull UUID inviterUuid, @Nonnull UUID inviteeUuid) {
        if (isInParty(inviteeUuid)) {
            return false;
        }

        Party party = getPartyByPlayer(inviterUuid);
        if (party == null) {
            party = createParty(inviterUuid);
        }
        if (party == null) {
            return false;
        }

        PartyInvite invite = new PartyInvite(inviterUuid, inviteeUuid, party.getId(), 60);
        pendingInvites.put(inviteeUuid, invite);
        return true;
    }

    @Nullable
    public Party acceptInvite(@Nonnull UUID inviteeUuid) {
        PartyInvite invite = pendingInvites.remove(inviteeUuid);
        if (invite == null || invite.isExpired()) {
            return null;
        }

        Party party = parties.get(invite.getPartyId());
        if (party == null) {
            return null;
        }

        party.addMember(inviteeUuid);
        playerPartyMap.put(inviteeUuid, party.getId());

        PlayerRef inviteeRef = Universe.get().getPlayer(inviteeUuid);
        String inviteeName = inviteeRef != null ? inviteeRef.getUsername() : inviteeUuid.toString();
        broadcastToParty(party, Message.raw(inviteeName + " joined the party."));
        return party;
    }

    public boolean declineInvite(@Nonnull UUID inviteeUuid) {
        return pendingInvites.remove(inviteeUuid) != null;
    }

    @Nullable
    public PartyInvite getPendingInvite(@Nonnull UUID inviteeUuid) {
        PartyInvite invite = pendingInvites.get(inviteeUuid);
        if (invite != null && invite.isExpired()) {
            pendingInvites.remove(inviteeUuid);
            return null;
        }
        return invite;
    }

    public boolean leaveParty(@Nonnull UUID playerUuid, boolean wasKicked) {
        String partyId = playerPartyMap.get(playerUuid);
        if (partyId == null) return false;

        Party party = parties.get(partyId);
        if (party == null) {
            playerPartyMap.remove(playerUuid);
            return false;
        }

        PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
        String playerName = playerRef != null ? playerRef.getUsername() : playerUuid.toString();

        if (party.isLeader(playerUuid)) {
            UUID newLeader = party.getNextLeaderCandidate();
            if (newLeader != null) {
                party.transferLeadership(newLeader);
                party.removeMember(playerUuid);
                playerPartyMap.remove(playerUuid);
                broadcastToParty(party, Message.raw(playerName + " left the party."));
            } else {
                disbandParty(partyId);
                return true;
            }
        } else {
            party.removeMember(playerUuid);
            playerPartyMap.remove(playerUuid);
            String message = wasKicked ? playerName + " was kicked from the party." : playerName + " left the party.";
            broadcastToParty(party, Message.raw(message));
        }

        if (wasKicked && playerRef != null) {
            playerRef.sendMessage(Message.raw("You have been kicked from the party."));
        }
        return true;
    }

    public boolean kickPlayer(@Nonnull UUID leaderUuid, @Nonnull UUID targetUuid) {
        Party party = getPartyByPlayer(leaderUuid);
        if (party == null || !party.isLeader(leaderUuid)) {
            return false;
        }
        if (!party.isMember(targetUuid)) {
            return false;
        }
        return leaveParty(targetUuid, true);
    }

    public void broadcastToParty(@Nonnull Party party, @Nonnull Message message) {
        for (UUID memberUuid : party.getMemberUuids()) {
            PlayerRef playerRef = Universe.get().getPlayer(memberUuid);
            if (playerRef != null) {
                playerRef.sendMessage(message);
            }
        }
    }
}
