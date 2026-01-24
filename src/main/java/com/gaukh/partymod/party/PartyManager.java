package com.gaukh.partymod.party;

import com.gaukh.partymod.events.*;
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
    private final Map<String, List<PartyJoinRequest>> joinRequests = new ConcurrentHashMap<>();

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
        String name = Party.getPlayerName(leaderUuid) + "'s Party";
        return createParty(leaderUuid, name);
    }

    @Nullable
    public Party createParty(@Nonnull UUID leaderUuid, @Nonnull String name) {
        LOGGER.atInfo().log("[DEBUG] createParty called for %s", leaderUuid);

        if (isInParty(leaderUuid)) {
            LOGGER.atInfo().log("[DEBUG] Player already in party, returning null");
            return null;
        }

        Party party = new Party(leaderUuid, name);
        parties.put(party.getId(), party);
        playerPartyMap.put(leaderUuid, party.getId());

        try {
            PartyStorage.saveParty(party);
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to save party to storage");
        }

        // Fire event after party is fully created
        LOGGER.atInfo().log("[DEBUG] Firing PartyCreateEvent");
        PartyEventBus.fire(new PartyCreateEvent(party));
        LOGGER.atInfo().log("[DEBUG] PartyCreateEvent fired");

        return party;
    }

    public void disbandParty(@Nonnull String partyId) {
        Party party = parties.remove(partyId);
        if (party == null) return;

        // Capture member UUIDs and leader before removing from maps
        Set<UUID> formerMembers = Set.copyOf(party.getMemberUuids());
        UUID leaderUuid = party.getLeaderUuid();

        for (UUID memberUuid : formerMembers) {
            playerPartyMap.remove(memberUuid);
        }
        broadcastToParty(party, Message.raw("Party has been disbanded."));

        try {
            PartyStorage.deleteParty(partyId);
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to delete party from storage");
        }

        // Fire event after cleanup
        PartyEventBus.fire(new PartyDisbandEvent(partyId, leaderUuid, formerMembers));
    }

    @Nullable
    public Party getPartyByPlayer(@Nonnull UUID playerUuid) {
        String partyId = playerPartyMap.get(playerUuid);
        return partyId != null ? parties.get(partyId) : null;
    }

    public boolean isInParty(@Nonnull UUID playerUuid) {
        return playerPartyMap.containsKey(playerUuid);
    }

    /**
     * Get all parties (for iteration purposes like HUD updates).
     */
    @Nonnull
    public Collection<Party> getAllParties() {
        return parties.values();
    }

    /**
     * Directly add a player to the party map (for debug/testing purposes).
     * This bypasses the invite system.
     */
    public void addPlayerToPartyMap(@Nonnull UUID playerUuid, @Nonnull Party party) {
        playerPartyMap.put(playerUuid, party.getId());
        try {
            PartyStorage.addMember(party.getId(), playerUuid);
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to add member to storage");
        }
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

        String inviteeName = Party.getPlayerName(inviteeUuid);
        broadcastToParty(party, Message.raw(inviteeName + " joined the party."));

        // Fire join event
        PartyEventBus.fire(new PartyJoinEvent(party, inviteeUuid));

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

        String playerName = Party.getPlayerName(playerUuid);

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

                // Fire leave event (party still exists with new leader)
                PartyEventBus.fire(new PartyLeaveEvent(party, partyId, playerUuid, wasKicked));
            } else {
                // Party will be disbanded - disbandParty fires its own event
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

            // Fire leave event
            PartyEventBus.fire(new PartyLeaveEvent(party, partyId, playerUuid, wasKicked));
        }

        if (wasKicked) {
            Party.sendMessageToPlayer(playerUuid, Message.raw("You have been kicked from the party."));
        }
        return true;
    }

    public boolean kickPlayer(@Nonnull UUID actorUuid, @Nonnull UUID targetUuid) {
        Party party = getPartyByPlayer(actorUuid);
        if (party == null || party.isMember(targetUuid)) {
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
        if (party == null || party.isMember(targetUuid)) {
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

            String targetName = Party.getPlayerName(targetUuid);
            broadcastToParty(party, Message.raw(targetName + " was promoted to " + party.getRoleDisplayName(targetUuid) + "."));
        }
        return promoted;
    }

    public boolean demotePlayer(@Nonnull UUID actorUuid, @Nonnull UUID targetUuid) {
        Party party = getPartyByPlayer(actorUuid);
        if (party == null || party.isMember(targetUuid)) {
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

            String targetName = Party.getPlayerName(targetUuid);
            broadcastToParty(party, Message.raw(targetName + " was demoted to " + party.getRoleDisplayName(targetUuid) + "."));
        }
        return demoted;
    }

    public boolean transferLeadership(@Nonnull UUID leaderUuid, @Nonnull UUID newLeaderUuid) {
        Party party = getPartyByPlayer(leaderUuid);
        if (party == null || !party.isLeader(leaderUuid)) {
            return false;
        }
        if (party.isMember(newLeaderUuid) || party.isLeader(newLeaderUuid)) {
            return false;
        }

        party.transferLeadership(newLeaderUuid);

        try {
            PartyStorage.updateLeader(party.getId(), newLeaderUuid);
            PartyStorage.updateMemberRole(party.getId(), leaderUuid, party.getRole(leaderUuid));
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to update leadership in storage");
        }

        String newLeaderName = Party.getPlayerName(newLeaderUuid);
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

    // ==================== New methods for public parties and join requests ====================

    /**
     * Get all publicly visible parties (not LOCKED and not full).
     */
    @Nonnull
    public List<Party> getPublicParties() {
        return parties.values().stream()
                .filter(p -> p.getAccessType().isPubliclyVisible())
                .filter(p -> !p.isFull())
                .toList();
    }

    /**
     * Get a party by its ID.
     */
    @Nullable
    public Party getPartyById(@Nonnull String partyId) {
        return parties.get(partyId);
    }

    /**
     * Join an OPEN party directly.
     */
    public boolean joinOpenParty(@Nonnull UUID playerUuid, @Nonnull String partyId) {
        if (isInParty(playerUuid)) return false;

        Party party = parties.get(partyId);
        if (party == null) return false;
        if (party.getAccessType() != PartyAccessType.OPEN) return false;
        if (party.isFull()) return false;

        party.addMember(playerUuid);
        playerPartyMap.put(playerUuid, party.getId());

        try {
            PartyStorage.addMember(party.getId(), playerUuid);
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to add member to storage");
        }

        String playerName = Party.getPlayerName(playerUuid);
        broadcastToParty(party, Message.raw(playerName + " joined the party."));

        // Fire join event
        PartyEventBus.fire(new PartyJoinEvent(party, playerUuid));

        return true;
    }

    /**
     * Join a PASSWORDED party with a password.
     */
    public boolean joinWithPassword(@Nonnull UUID playerUuid, @Nonnull String partyId, @Nonnull String password) {
        if (isInParty(playerUuid)) return false;

        Party party = parties.get(partyId);
        if (party == null) return false;
        if (party.getAccessType() != PartyAccessType.PASSWORDED) return false;
        if (party.isFull()) return false;
        if (!party.checkPassword(password)) return false;

        party.addMember(playerUuid);
        playerPartyMap.put(playerUuid, party.getId());

        try {
            PartyStorage.addMember(party.getId(), playerUuid);
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to add member to storage");
        }

        String playerName = Party.getPlayerName(playerUuid);
        broadcastToParty(party, Message.raw(playerName + " joined the party."));

        // Fire join event
        PartyEventBus.fire(new PartyJoinEvent(party, playerUuid));

        return true;
    }

    /**
     * Send a join request to a REQUEST_ONLY party.
     */
    public boolean sendJoinRequest(@Nonnull UUID playerUuid, @Nonnull String partyId) {
        if (isInParty(playerUuid)) return false;

        Party party = parties.get(partyId);
        if (party == null) return false;
        if (party.getAccessType() != PartyAccessType.REQUEST_ONLY) return false;
        if (party.isFull()) return false;

        // Check if already has a pending request
        List<PartyJoinRequest> requests = joinRequests.computeIfAbsent(partyId, k -> new ArrayList<>());
        boolean alreadyRequested = requests.stream()
                .anyMatch(r -> r.getRequesterUuid().equals(playerUuid) && !r.isExpired());
        if (alreadyRequested) return false;

        PartyJoinRequest request = new PartyJoinRequest(playerUuid, partyId);
        requests.add(request);

        try {
            PartyStorage.saveJoinRequest(request);
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to save join request");
        }

        // Notify leader
        PlayerRef leaderRef = Universe.get().getPlayer(party.getLeaderUuid());
        if (leaderRef != null) {
            String requesterName = Party.getPlayerName(playerUuid);
            leaderRef.sendMessage(Message.raw(requesterName + " wants to join your party."));
        }

        return true;
    }

    /**
     * Accept a join request (leader only).
     */
    public boolean acceptJoinRequest(@Nonnull UUID leaderUuid, @Nonnull UUID requesterUuid) {
        Party party = getPartyByPlayer(leaderUuid);
        if (party == null || !party.isLeader(leaderUuid)) return false;
        if (party.isFull()) return false;
        if (isInParty(requesterUuid)) return false;

        List<PartyJoinRequest> requests = joinRequests.get(party.getId());
        if (requests == null) return false;

        PartyJoinRequest request = requests.stream()
                .filter(r -> r.getRequesterUuid().equals(requesterUuid) && !r.isExpired())
                .findFirst()
                .orElse(null);

        if (request == null) return false;

        // Remove request
        requests.remove(request);
        try {
            PartyStorage.deleteJoinRequest(party.getId(), requesterUuid);
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to delete join request");
        }

        // Add member
        party.addMember(requesterUuid);
        playerPartyMap.put(requesterUuid, party.getId());

        try {
            PartyStorage.addMember(party.getId(), requesterUuid);
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to add member to storage");
        }

        String requesterName = Party.getPlayerName(requesterUuid);
        broadcastToParty(party, Message.raw(requesterName + " joined the party."));

        Party.sendMessageToPlayer(requesterUuid, Message.raw("Your join request was accepted!"));

        // Fire join event
        PartyEventBus.fire(new PartyJoinEvent(party, requesterUuid));

        return true;
    }

    /**
     * Decline a join request (leader only).
     */
    public boolean declineJoinRequest(@Nonnull UUID leaderUuid, @Nonnull UUID requesterUuid) {
        Party party = getPartyByPlayer(leaderUuid);
        if (party == null || !party.isLeader(leaderUuid)) return false;

        List<PartyJoinRequest> requests = joinRequests.get(party.getId());
        if (requests == null) return false;

        boolean removed = requests.removeIf(r -> r.getRequesterUuid().equals(requesterUuid));
        if (removed) {
            try {
                PartyStorage.deleteJoinRequest(party.getId(), requesterUuid);
            } catch (SQLException e) {
                LOGGER.atWarning().withCause(e).log("Failed to delete join request");
            }

            Party.sendMessageToPlayer(requesterUuid, Message.raw("Your join request was declined."));
        }
        return removed;
    }

    /**
     * Update party settings (leader only).
     */
    public boolean updatePartySettings(@Nonnull UUID leaderUuid, @Nullable String name,
                                       @Nullable String password, @Nullable PartyAccessType accessType,
                                       @Nullable Integer maxMembers) {
        Party party = getPartyByPlayer(leaderUuid);
        if (party == null || !party.isLeader(leaderUuid)) return false;

        if (name != null && !name.isBlank()) party.setName(name);
        if (password != null) party.setPassword(password.isEmpty() ? null : password);
        if (accessType != null) party.setAccessType(accessType);
        if (maxMembers != null) party.setMaxMembers(maxMembers);

        try {
            PartyStorage.updatePartySettings(party);
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to update party settings");
        }

        return true;
    }

    /**
     * Get all non-expired join requests for a party.
     */
    @Nonnull
    public List<PartyJoinRequest> getJoinRequests(@Nonnull String partyId) {
        List<PartyJoinRequest> requests = joinRequests.get(partyId);
        if (requests == null) return List.of();
        // Remove expired
        requests.removeIf(PartyJoinRequest::isExpired);
        return new ArrayList<>(requests);
    }

    /**
     * Check if a player has a pending join request for a specific party.
     */
    public boolean hasPendingJoinRequest(@Nonnull UUID playerUuid, @Nonnull String partyId) {
        List<PartyJoinRequest> requests = joinRequests.get(partyId);
        if (requests == null) return false;
        return requests.stream()
                .anyMatch(r -> r.getRequesterUuid().equals(playerUuid) && !r.isExpired());
    }
}
