package com.gaukh.partymod.party;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
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

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<String, Party> parties = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerPartyMap = new ConcurrentHashMap<>();
    private final Map<UUID, PartyInvite> pendingInvites = new ConcurrentHashMap<>();

    public PartyManager() {
        try {
            List<Party> loadedParties = PartyStorage.loadAllParties();
            LOGGER.atInfo().log("Loading %d parties from storage", loadedParties.size());
            for (Party party : loadedParties) {
                parties.put(party.getId(), party);
                party.getMemberUuids().forEach(uuid -> playerPartyMap.put(uuid, party.getId()));
                LOGGER.atInfo().log("Loaded party %s with %d members", party.getId(), party.getMemberCount());
            }
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load parties from storage");
        }
    }

    @Nullable
    public Party createParty(@Nonnull UUID leaderUuid) {
        if (isInParty(leaderUuid)) {
            return null;
        }

        Party party = new Party(leaderUuid);
        parties.put(party.getId(), party);
        playerPartyMap.put(leaderUuid, party.getId());

        try {
            PartyStorage.saveParty(party);
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to save party to storage");
        }

        return party;
    }

    public void disbandParty(@Nonnull String partyId) {
        Party party = parties.remove(partyId);
        if (party == null) return;

        for (UUID memberUuid : party.getMemberUuids()) {
            playerPartyMap.remove(memberUuid);
        }
        broadcastToParty(party, Message.raw("Party has been disbanded."));

        try {
            PartyStorage.deleteParty(partyId);
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to delete party from storage");
        }
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

        // Check if inviter has permission to invite (Leader, Admin, Moderator, or Member)
        if (!party.isLeader(inviterUuid)) {
            PartyRole inviterRole = party.getRole(inviterUuid);
            if (inviterRole.getLevel() < PartyRole.MEMBER.getLevel()) {
                return false; // Guests cannot invite
            }
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

        try {
            PartyStorage.addMember(party.getId(), inviteeUuid);
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to add member to storage");
        }

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

                try {
                    PartyStorage.updateLeader(partyId, newLeader);
                    PartyStorage.removeMember(partyId, playerUuid);
                } catch (SQLException e) {
                    LOGGER.atWarning().withCause(e).log("Failed to update storage after leader left");
                }
            } else {
                disbandParty(partyId);
                return true;
            }
        } else {
            party.removeMember(playerUuid);
            playerPartyMap.remove(playerUuid);
            String message = wasKicked ? playerName + " was kicked from the party." : playerName + " left the party.";
            broadcastToParty(party, Message.raw(message));

            try {
                PartyStorage.removeMember(partyId, playerUuid);
            } catch (SQLException e) {
                LOGGER.atWarning().withCause(e).log("Failed to remove member from storage");
            }
        }

        if (wasKicked && playerRef != null) {
            playerRef.sendMessage(Message.raw("You have been kicked from the party."));
        }
        return true;
    }

    public boolean kickPlayer(@Nonnull UUID actorUuid, @Nonnull UUID targetUuid) {
        Party party = getPartyByPlayer(actorUuid);
        if (party == null || !party.isMember(targetUuid)) {
            return false;
        }
        // Leader can kick anyone, others need higher role
        if (!party.isLeader(actorUuid)) {
            PartyRole actorRole = party.getRole(actorUuid);
            PartyRole targetRole = party.getRole(targetUuid);
            if (!actorRole.canKick(targetRole)) {
                return false;
            }
        }
        return leaveParty(targetUuid, true);
    }

    public boolean promotePlayer(@Nonnull UUID actorUuid, @Nonnull UUID targetUuid) {
        Party party = getPartyByPlayer(actorUuid);
        if (party == null || !party.isMember(targetUuid)) {
            return false;
        }
        // Only leader can promote
        if (!party.isLeader(actorUuid)) {
            return false;
        }
        if (party.isLeader(targetUuid)) {
            return false;
        }

        boolean promoted = party.promote(targetUuid);
        if (promoted) {
            try {
                PartyStorage.updateMemberRole(party.getId(), targetUuid, party.getRole(targetUuid));
            } catch (SQLException e) {
                LOGGER.atWarning().withCause(e).log("Failed to update role in storage");
            }

            PlayerRef targetRef = Universe.get().getPlayer(targetUuid);
            String targetName = targetRef != null ? targetRef.getUsername() : targetUuid.toString();
            broadcastToParty(party, Message.raw(targetName + " was promoted to " + party.getRoleDisplayName(targetUuid) + "."));
        }
        return promoted;
    }

    public boolean demotePlayer(@Nonnull UUID actorUuid, @Nonnull UUID targetUuid) {
        Party party = getPartyByPlayer(actorUuid);
        if (party == null || !party.isMember(targetUuid)) {
            return false;
        }
        // Only leader can demote
        if (!party.isLeader(actorUuid)) {
            return false;
        }
        if (party.isLeader(targetUuid)) {
            return false;
        }

        boolean demoted = party.demote(targetUuid);
        if (demoted) {
            try {
                PartyStorage.updateMemberRole(party.getId(), targetUuid, party.getRole(targetUuid));
            } catch (SQLException e) {
                LOGGER.atWarning().withCause(e).log("Failed to update role in storage");
            }

            PlayerRef targetRef = Universe.get().getPlayer(targetUuid);
            String targetName = targetRef != null ? targetRef.getUsername() : targetUuid.toString();
            broadcastToParty(party, Message.raw(targetName + " was demoted to " + party.getRoleDisplayName(targetUuid) + "."));
        }
        return demoted;
    }

    public boolean transferLeadership(@Nonnull UUID leaderUuid, @Nonnull UUID newLeaderUuid) {
        Party party = getPartyByPlayer(leaderUuid);
        if (party == null || !party.isLeader(leaderUuid)) {
            return false;
        }
        if (!party.isMember(newLeaderUuid) || party.isLeader(newLeaderUuid)) {
            return false;
        }

        party.transferLeadership(newLeaderUuid);

        try {
            PartyStorage.updateLeader(party.getId(), newLeaderUuid);
            PartyStorage.updateMemberRole(party.getId(), leaderUuid, party.getRole(leaderUuid));
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to update leadership in storage");
        }

        PlayerRef newLeaderRef = Universe.get().getPlayer(newLeaderUuid);
        String newLeaderName = newLeaderRef != null ? newLeaderRef.getUsername() : newLeaderUuid.toString();
        broadcastToParty(party, Message.raw(newLeaderName + " is now the party leader."));
        return true;
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
