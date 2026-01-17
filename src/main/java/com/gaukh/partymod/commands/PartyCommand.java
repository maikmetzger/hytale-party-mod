package com.gaukh.partymod.commands;

import com.gaukh.partymod.PartyMod;
import com.gaukh.partymod.party.Party;
import com.gaukh.partymod.party.PartyInvite;
import com.gaukh.partymod.party.PartyManager;
import com.gaukh.partymod.pages.PartyMenuPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Party command for managing parties.
 *
 * Usage:
 * /party - Show party info
 * /party invite <player> - Invite a player
 * /party accept - Accept pending invite
 * /party decline - Decline pending invite
 * /party leave - Leave current party
 * /party kick <player> - Kick a player (leader only)
 * /party disband - Disband the party (leader only)
 * /party list - List party members
 */
public class PartyCommand extends AbstractPlayerCommand {

    private final PartyMod plugin;
    private final PartyManager partyManager;

    public PartyCommand(@Nonnull PartyMod plugin) {
        super("party", "Party management commands");
        this.plugin = plugin;
        this.partyManager = plugin.getPartyManager();
        setAllowsExtraArguments(true); // Allow subcommands and args
    }

    @Override
    protected boolean canGeneratePermission() {
        return false; // Allow all players to use this command
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                          @Nonnull Store<EntityStore> store,
                          @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef playerRef,
                          @Nonnull World world) {
        UUID playerUuid = playerRef.getUuid();

        // Parse args from input string
        // Format: "/party [subcommand] [args...]"
        String input = context.getInputString().trim();
        String[] parts = input.split("\\s+");

        // parts[0] is "party" (the command name)
        String subcommand = parts.length > 1 ? parts[1] : null;

        if (subcommand == null) {
            // No subcommand - open party UI
            openPartyUI(store, ref, playerRef);
            return;
        }

        switch (subcommand.toLowerCase()) {
            case "create" -> handleCreate(context, store, ref, playerRef, playerUuid, parts);
            case "invite" -> handleInvite(context, playerRef, playerUuid, parts);
            case "accept" -> handleAccept(context, playerRef, playerUuid);
            case "decline" -> handleDecline(context, playerRef, playerUuid);
            case "leave" -> handleLeave(context, playerRef, playerUuid);
            case "kick" -> handleKick(context, playerRef, playerUuid, parts);
            case "disband" -> handleDisband(context, playerRef, playerUuid);
            case "list" -> handleList(context, playerRef, playerUuid);
            default -> showUsage(context);
        }
    }

    private void showUsage(@Nonnull CommandContext context) {
        context.sendMessage(Message.raw(
                "Usage:\n" +
                "/party - Show party info\n" +
                "/party invite <player> - Invite a player\n" +
                "/party accept - Accept pending invite\n" +
                "/party decline - Decline pending invite\n" +
                "/party leave - Leave current party\n" +
                "/party kick <player> - Kick a player (leader only)\n" +
                "/party disband - Disband the party (leader only)\n" +
                "/party list - List party members"
        ));
    }

    private void showPartyInfo(@Nonnull CommandContext context,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull UUID playerUuid) {
        Party party = partyManager.getPartyByPlayer(playerUuid);
        PartyInvite pendingInvite = partyManager.getPendingInvite(playerUuid);

        StringBuilder sb = new StringBuilder();
        sb.append("=== Party Menu ===\n");

        // Show pending invite
        if (pendingInvite != null) {
            PlayerRef inviterRef = Universe.get().getPlayer(pendingInvite.getInviterUuid());
            String inviterName = inviterRef != null ? inviterRef.getUsername() : "Unknown";
            sb.append("Pending invite from: ").append(inviterName).append("\n");
            sb.append("  /party accept - Accept invite\n");
            sb.append("  /party decline - Decline invite\n\n");
        }

        if (party != null) {
            sb.append("Your Party:\n");
            for (UUID memberUuid : party.getMemberUuids()) {
                PlayerRef memberRef = Universe.get().getPlayer(memberUuid);
                String memberName = memberRef != null ? memberRef.getUsername() : memberUuid.toString().substring(0, 8);
                String leaderTag = party.isLeader(memberUuid) ? " [Leader]" : "";
                String onlineTag = memberRef != null ? "" : " (Offline)";
                sb.append("  - ").append(memberName).append(leaderTag).append(onlineTag).append("\n");
            }
            sb.append("\nCommands:\n");
            sb.append("  /party leave - Leave party\n");
            if (party.isLeader(playerUuid)) {
                sb.append("  /party kick <player> - Kick player\n");
                sb.append("  /party disband - Disband party\n");
            }
        } else if (pendingInvite == null) {
            sb.append("You are not in a party.\n\n");
            sb.append("Commands:\n");
            sb.append("  /party invite <player> - Invite a player\n");
        }

        context.sendMessage(Message.raw(sb.toString()));
    }

    private void handleInvite(@Nonnull CommandContext context,
                              @Nonnull PlayerRef playerRef,
                              @Nonnull UUID playerUuid,
                              @Nonnull String[] parts) {
        if (parts.length < 3) {
            context.sendMessage(Message.raw("Usage: /party invite <player>"));
            return;
        }

        String targetName = parts[2];

        // Find target player
        PlayerRef targetRef = findPlayerByName(targetName);

        if (targetRef == null) {
            context.sendMessage(Message.raw("Player not found: " + targetName));
            return;
        }

        UUID targetUuid = targetRef.getUuid();

        // Check if already in a party
        if (partyManager.isInParty(targetUuid)) {
            context.sendMessage(Message.raw("That player is already in a party."));
            return;
        }

        // Send invite
        boolean sent = partyManager.sendInvite(playerUuid, targetUuid);
        if (sent) {
            context.sendMessage(Message.raw("Invitation sent to " + targetRef.getUsername()));
            targetRef.sendMessage(Message.raw(playerRef.getUsername() + " has invited you to their party. Use /party accept to join."));
        } else {
            context.sendMessage(Message.raw("Failed to send invite."));
        }
    }

    private void handleAccept(@Nonnull CommandContext context,
                              @Nonnull PlayerRef playerRef,
                              @Nonnull UUID playerUuid) {
        PartyInvite invite = partyManager.getPendingInvite(playerUuid);
        if (invite == null) {
            context.sendMessage(Message.raw("You have no pending party invitation."));
            return;
        }

        Party party = partyManager.acceptInvite(playerUuid);
        if (party != null) {
            PlayerRef inviterRef = Universe.get().getPlayer(invite.getInviterUuid());
            String inviterName = inviterRef != null ? inviterRef.getUsername() : "Unknown";
            context.sendMessage(Message.raw("You joined " + inviterName + "'s party!"));
        } else {
            context.sendMessage(Message.raw("The party invitation has expired."));
        }
    }

    private void handleDecline(@Nonnull CommandContext context,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull UUID playerUuid) {
        boolean declined = partyManager.declineInvite(playerUuid);
        if (declined) {
            context.sendMessage(Message.raw("You declined the party invitation."));
        } else {
            context.sendMessage(Message.raw("You have no pending party invitation."));
        }
    }

    private void handleLeave(@Nonnull CommandContext context,
                             @Nonnull PlayerRef playerRef,
                             @Nonnull UUID playerUuid) {
        if (!partyManager.isInParty(playerUuid)) {
            context.sendMessage(Message.raw("You are not in a party."));
            return;
        }

        partyManager.leaveParty(playerUuid, false);
        context.sendMessage(Message.raw("You left the party."));
    }

    private void handleKick(@Nonnull CommandContext context,
                            @Nonnull PlayerRef playerRef,
                            @Nonnull UUID playerUuid,
                            @Nonnull String[] parts) {
        if (parts.length < 3) {
            context.sendMessage(Message.raw("Usage: /party kick <player>"));
            return;
        }

        String targetName = parts[2];

        Party party = partyManager.getPartyByPlayer(playerUuid);
        if (party == null) {
            context.sendMessage(Message.raw("You are not in a party."));
            return;
        }

        if (!party.isLeader(playerUuid)) {
            context.sendMessage(Message.raw("Only the party leader can kick players."));
            return;
        }

        // Find target player
        PlayerRef targetRef = findPlayerByName(targetName);

        if (targetRef == null) {
            context.sendMessage(Message.raw("Player not found: " + targetName));
            return;
        }

        boolean kicked = partyManager.kickPlayer(playerUuid, targetRef.getUuid());
        if (!kicked) {
            context.sendMessage(Message.raw("Could not kick that player."));
        }
    }

    private void handleDisband(@Nonnull CommandContext context,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull UUID playerUuid) {
        Party party = partyManager.getPartyByPlayer(playerUuid);
        if (party == null) {
            context.sendMessage(Message.raw("You are not in a party."));
            return;
        }

        if (!party.isLeader(playerUuid)) {
            context.sendMessage(Message.raw("Only the party leader can disband the party."));
            return;
        }

        partyManager.disbandParty(party.getId());
        context.sendMessage(Message.raw("Party disbanded."));
    }

    private void handleList(@Nonnull CommandContext context,
                            @Nonnull PlayerRef playerRef,
                            @Nonnull UUID playerUuid) {
        Party party = partyManager.getPartyByPlayer(playerUuid);
        if (party == null) {
            context.sendMessage(Message.raw("You are not in a party."));
            return;
        }

        StringBuilder sb = new StringBuilder("Party Members:\n");
        for (UUID memberUuid : party.getMemberUuids()) {
            PlayerRef memberRef = Universe.get().getPlayer(memberUuid);
            String name = memberRef != null ? memberRef.getUsername() : memberUuid.toString();
            String leaderTag = party.isLeader(memberUuid) ? " [Leader]" : "";
            String onlineTag = memberRef != null ? "" : " (Offline)";
            sb.append("- ").append(name).append(leaderTag).append(onlineTag).append("\n");
        }

        context.sendMessage(Message.raw(sb.toString()));
    }

    private PlayerRef findPlayerByName(@Nonnull String name) {
        for (PlayerRef player : Universe.get().getPlayers()) {
            if (player.getUsername().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }

    private void openPartyUI(@Nonnull Store<EntityStore> store,
                             @Nonnull Ref<EntityStore> ref,
                             @Nonnull PlayerRef playerRef) {
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent != null) {
            PartyMenuPage menuUI = new PartyMenuPage(playerRef, plugin);
            playerComponent.getPageManager().openCustomPage(ref, store, menuUI);
        }
    }

    private void handleCreate(@Nonnull CommandContext context,
                              @Nonnull Store<EntityStore> store,
                              @Nonnull Ref<EntityStore> ref,
                              @Nonnull PlayerRef playerRef,
                              @Nonnull UUID playerUuid,
                              @Nonnull String[] parts) {
        // Check if already in a party
        if (partyManager.isInParty(playerUuid)) {
            context.sendMessage(Message.raw("You are already in a party."));
            return;
        }

        // Create party
        Party party = partyManager.createParty(playerUuid);
        if (party != null) {
            context.sendMessage(Message.raw("Party created!"));
            // Open the party UI after creation
            openPartyUI(store, ref, playerRef);
        } else {
            context.sendMessage(Message.raw("Failed to create party."));
        }
    }
}
