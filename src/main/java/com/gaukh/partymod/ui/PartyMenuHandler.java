package com.gaukh.partymod.ui;

import com.gaukh.partymod.party.Party;
import com.gaukh.partymod.party.PartyManager;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public class PartyMenuHandler {

    private final PartyManager partyManager;
    private final PlayerRef playerRef;
    private final UUID playerUuid;

    private final Map<String, Function<String, Boolean>> actions = Map.of(
        "create",  this::handleCreate,
        "accept",  this::handleAccept,
        "decline", this::handleDecline,
        "invite",  this::handleInvite,
        "kick",    this::handleKick,
        "leave",   this::handleLeave,
        "disband", this::handleDisband,
        "close",   target -> false
    );

    public PartyMenuHandler(@Nonnull PartyManager partyManager, @Nonnull PlayerRef playerRef) {
        this.partyManager = partyManager;
        this.playerRef = playerRef;
        this.playerUuid = playerRef.getUuid();
    }

    /**
     * @return true = refresh UI, false = close UI
     */
    public boolean handle(@Nonnull String action, @Nullable String target) {
        var handler = actions.get(action);
        return handler == null || handler.apply(target);
    }

    private Boolean handleCreate(String target) {
        Party party = partyManager.createParty(playerUuid);
        if (party != null) {
            playerRef.sendMessage(Message.raw("Party created!"));
        }
        return true;
    }

    private Boolean handleAccept(String target) {
        Party party = partyManager.acceptInvite(playerUuid);
        if (party != null) {
            playerRef.sendMessage(Message.raw("You joined the party!"));
        } else {
            playerRef.sendMessage(Message.raw("The invitation has expired."));
        }
        return true;
    }

    private Boolean handleDecline(String target) {
        partyManager.declineInvite(playerUuid);
        playerRef.sendMessage(Message.raw("You declined the invitation."));
        return true;
    }

    private Boolean handleInvite(String target) {
        if (target == null) return true;

        UUID targetUuid = UUID.fromString(target);
        PlayerRef targetRef = Universe.get().getPlayer(targetUuid);
        if (targetRef != null && partyManager.sendInvite(playerUuid, targetUuid)) {
            playerRef.sendMessage(Message.raw("Invitation sent to " + targetRef.getUsername()));
            targetRef.sendMessage(Message.raw(playerRef.getUsername() + " has invited you to their party. Use /party accept to join."));
        }
        return true;
    }

    private Boolean handleKick(String target) {
        if (target != null) {
            partyManager.kickPlayer(playerUuid, UUID.fromString(target));
        }
        return true;
    }

    private Boolean handleLeave(String target) {
        partyManager.leaveParty(playerUuid, false);
        playerRef.sendMessage(Message.raw("You left the party."));
        return false;
    }

    private Boolean handleDisband(String target) {
        Party party = partyManager.getPartyByPlayer(playerUuid);
        if (party != null) {
            partyManager.disbandParty(party.getId());
            playerRef.sendMessage(Message.raw("Party disbanded."));
        }
        return false;
    }
}
