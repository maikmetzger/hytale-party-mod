package com.gaukh.partymod.party;

import com.gaukh.partymod.PartyMod;
import com.gaukh.partymod.data.PartyDataStore;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages all parties and party invitations.
 */
public class PartyManager {

    private final PartyMod plugin;

    // Party ID -> Party
    private final Map<String, Party> parties = new ConcurrentHashMap<>();

    // Player UUID -> Party ID (quick lookup)
    private final Map<UUID, String> playerPartyMap = new ConcurrentHashMap<>();

    // Invitee UUID -> PartyInvite (only one pending invite per player)
    private final Map<UUID, PartyInvite> pendingInvites = new ConcurrentHashMap<>();

    private PartyDataStore dataStore;

    public PartyManager(@Nonnull PartyMod plugin) {
        this.plugin = plugin;
    }

    public void registerEvents() {
        // Handle player disconnect - remove from party or transfer leadership
        plugin.getEventRegistry().register(PlayerDisconnectEvent.class, event -> {
            UUID playerUuid = event.getPlayerRef().getUuid();
            handlePlayerDisconnect(playerUuid);
        });
    }

    public void loadData() {
        dataStore = new PartyDataStore(plugin);
        Map<String, Party> loadedParties = dataStore.loadAll();

        parties.clear();
        playerPartyMap.clear();

        for (Party party : loadedParties.values()) {
            parties.put(party.getId(), party);
            for (UUID memberUuid : party.getMemberUuids()) {
                playerPartyMap.put(memberUuid, party.getId());
            }
        }

        plugin.getLogger().at(Level.INFO).log("Loaded %d parties", parties.size());
    }

    public void saveData() {
        if (dataStore != null) {
            for (Party party : parties.values()) {
                dataStore.save(party.getId(), party);
            }
            plugin.getLogger().at(Level.INFO).log("Saved %d parties", parties.size());
        }
    }

    // Party Operations

    @Nullable
    public Party createParty(@Nonnull UUID leaderUuid) {
        if (isInParty(leaderUuid)) {
            return null;
        }

        Party party = new Party(leaderUuid);
        parties.put(party.getId(), party);
        playerPartyMap.put(leaderUuid, party.getId());

        plugin.getLogger().at(Level.INFO).log("Party created: %s by %s", party.getId(), leaderUuid);
        return party;
    }

    public boolean disbandParty(@Nonnull String partyId) {
        Party party = parties.remove(partyId);
        if (party == null) {
            return false;
        }

        for (UUID memberUuid : party.getMemberUuids()) {
            playerPartyMap.remove(memberUuid);
        }

        broadcastToParty(party, Message.raw("Party has been disbanded."));

        if (dataStore != null) {
            dataStore.delete(partyId);
        }

        plugin.getLogger().at(Level.INFO).log("Party disbanded: %s", partyId);
        return true;
    }

    @Nullable
    public Party getPartyByPlayer(@Nonnull UUID playerUuid) {
        String partyId = playerPartyMap.get(playerUuid);
        if (partyId == null) {
            return null;
        }
        return parties.get(partyId);
    }

    @Nullable
    public Party getPartyById(@Nonnull String partyId) {
        return parties.get(partyId);
    }

    public boolean isInParty(@Nonnull UUID playerUuid) {
        return playerPartyMap.containsKey(playerUuid);
    }

    public boolean areInSameParty(@Nonnull UUID uuid1, @Nonnull UUID uuid2) {
        String partyId1 = playerPartyMap.get(uuid1);
        String partyId2 = playerPartyMap.get(uuid2);
        return partyId1 != null && partyId1.equals(partyId2);
    }

    // Invite Operations

    public boolean sendInvite(@Nonnull UUID inviterUuid, @Nonnull UUID inviteeUuid) {
        if (isInParty(inviteeUuid)) {
            return false;
        }

        if (pendingInvites.containsKey(inviteeUuid)) {
            pendingInvites.remove(inviteeUuid);
        }

        Party party = getPartyByPlayer(inviterUuid);
        if (party == null) {
            party = createParty(inviterUuid);
        }

        if (party == null) {
            return false;
        }

        int maxSize = plugin.getPluginConfig().getMaxPartySize();
        if (maxSize > 0 && party.getMemberCount() >= maxSize) {
            return false;
        }

        int timeout = plugin.getPluginConfig().getInviteTimeoutSeconds();
        PartyInvite invite = new PartyInvite(inviterUuid, inviteeUuid, party.getId(), timeout);
        pendingInvites.put(inviteeUuid, invite);

        plugin.getLogger().at(Level.FINE).log("Invite sent: %s -> %s", inviterUuid, inviteeUuid);
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

        plugin.getLogger().at(Level.INFO).log("Player %s joined party %s", inviteeUuid, party.getId());
        return party;
    }

    public boolean declineInvite(@Nonnull UUID inviteeUuid) {
        PartyInvite invite = pendingInvites.remove(inviteeUuid);
        return invite != null;
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

    // Member Operations

    public boolean leaveParty(@Nonnull UUID playerUuid, boolean wasKicked) {
        String partyId = playerPartyMap.get(playerUuid);
        if (partyId == null) {
            return false;
        }

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

        plugin.getLogger().at(Level.INFO).log("Player %s left party %s (kicked=%s)", playerUuid, partyId, wasKicked);
        return true;
    }

    public boolean kickPlayer(@Nonnull UUID leaderUuid, @Nonnull UUID targetUuid) {
        Party party = getPartyByPlayer(leaderUuid);
        if (party == null || !party.isLeader(leaderUuid)) {
            return false;
        }

        if (!party.isMember(targetUuid) || party.isLeader(targetUuid)) {
            return false;
        }

        return leaveParty(targetUuid, true);
    }

    // Utility Methods

    public void broadcastToParty(@Nonnull Party party, @Nonnull Message message) {
        for (UUID memberUuid : party.getMemberUuids()) {
            PlayerRef playerRef = Universe.get().getPlayer(memberUuid);
            if (playerRef != null) {
                playerRef.sendMessage(message);
            }
        }
    }

    private void handlePlayerDisconnect(@Nonnull UUID playerUuid) {
        pendingInvites.remove(playerUuid);
        leaveParty(playerUuid, false);
    }

    @Nonnull
    public List<PlayerRef> getOnlinePartyMembers(@Nonnull Party party) {
        List<PlayerRef> online = new ArrayList<>();
        for (UUID memberUuid : party.getMemberUuids()) {
            PlayerRef playerRef = Universe.get().getPlayer(memberUuid);
            if (playerRef != null) {
                online.add(playerRef);
            }
        }
        return online;
    }

    @Nonnull
    public Collection<Party> getAllParties() {
        return Collections.unmodifiableCollection(parties.values());
    }
}
