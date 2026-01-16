package com.gaukh.partymod.ui;

import com.gaukh.partymod.PartyMod;
import com.gaukh.partymod.party.Party;
import com.gaukh.partymod.party.PartyInvite;
import com.gaukh.partymod.party.PartyManager;
import com.gaukh.partymod.party.PartyRole;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.util.TempAssetIdUtil;

import javax.annotation.Nonnull;
import java.util.UUID;

public class PartyMenuUI extends InteractiveCustomUIPage<PartyMenuEventData> {

    private static final int MAX_PARTY_SIZE = 8;

    private final PartyManager partyManager;
    private boolean showingInviteView = false;
    private UUID selectedPlayerUuid = null; // For player action view
    private String pendingConfirmAction = null; // "leave", "disband", or "transferLeadership"

    public PartyMenuUI(@Nonnull PlayerRef playerRef, @Nonnull PartyMod plugin) {
        super(playerRef, CustomPageLifetime.CanDismiss, PartyMenuEventData.CODEC);
        this.partyManager = plugin.getPartyManager();
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        cmd.append("PartyMenu.ui");
        bindStaticEvents(events);
        buildContent(cmd, events);
    }

    private void bindStaticEvents(@Nonnull UIEventBuilder events) {
        // Close button
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of("Action", "close"), false);

        // No Party View
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CreatePartyButton",
                EventData.of("Action", "create"), false);

        // Party View buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#InvitePlayerButton",
                EventData.of("Action", "showInvite"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#LeavePartyButton",
                EventData.of("Action", "leave"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DisbandButton",
                EventData.of("Action", "disband"), false);

        // Invite View
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BackToPartyButton",
                EventData.of("Action", "backToParty"), false);

        // Pending Invite buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#AcceptInvite",
                EventData.of("Action", "accept"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DeclineInvite",
                EventData.of("Action", "decline"), false);

        // Confirmation Dialog buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelConfirmButton",
                EventData.of("Action", "cancelConfirm"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmActionButton",
                EventData.of("Action", "executeConfirm"), false);

        // Player Action View buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BackToPartyFromAction",
                EventData.of("Action", "backToPartyFromAction"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PromoteButton",
                EventData.of("Action", "promote"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DemoteButton",
                EventData.of("Action", "demote"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#KickPlayerButton",
                EventData.of("Action", "kickSelected"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TransferLeadershipButton",
                EventData.of("Action", "confirmTransfer"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull PartyMenuEventData data) {
        String action = data.getAction();
        String target = data.getTarget();

        switch (action) {
            case "close" -> closePage(ref, store);
            case "create" -> {
                partyManager.createParty(playerRef.getUuid());
                refreshUI(ref, store);
            }
            case "showInvite" -> {
                showingInviteView = true;
                refreshUI(ref, store);
            }
            case "backToParty" -> {
                showingInviteView = false;
                refreshUI(ref, store);
            }
            case "invite" -> {
                if (target != null) {
                    UUID targetUuid = UUID.fromString(target);
                    partyManager.sendInvite(playerRef.getUuid(), targetUuid);
                    PlayerRef targetRef = Universe.get().getPlayer(targetUuid);
                    if (targetRef != null) {
                        targetRef.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                                playerRef.getUsername() + " invited you to their party!"));
                    }
                }
                refreshUI(ref, store);
            }
            case "kick" -> {
                if (target != null) {
                    partyManager.kickPlayer(playerRef.getUuid(), UUID.fromString(target));
                }
                refreshUI(ref, store);
            }
            case "leave" -> {
                pendingConfirmAction = "leave";
                refreshUI(ref, store);
            }
            case "disband" -> {
                pendingConfirmAction = "disband";
                refreshUI(ref, store);
            }
            case "cancelConfirm" -> {
                pendingConfirmAction = null;
                refreshUI(ref, store);
            }
            case "executeConfirm" -> {
                if ("leave".equals(pendingConfirmAction)) {
                    partyManager.leaveParty(playerRef.getUuid(), false);
                    closePage(ref, store);
                } else if ("disband".equals(pendingConfirmAction)) {
                    Party party = partyManager.getPartyByPlayer(playerRef.getUuid());
                    if (party != null) {
                        partyManager.disbandParty(party.getId());
                    }
                    closePage(ref, store);
                } else if ("transferLeadership".equals(pendingConfirmAction) && selectedPlayerUuid != null) {
                    partyManager.transferLeadership(playerRef.getUuid(), selectedPlayerUuid);
                    selectedPlayerUuid = null;
                    pendingConfirmAction = null;
                    refreshUI(ref, store);
                    return;
                }
                pendingConfirmAction = null;
            }
            case "selectPlayer" -> {
                if (target != null) {
                    selectedPlayerUuid = UUID.fromString(target);
                    refreshUI(ref, store);
                }
            }
            case "backToPartyFromAction" -> {
                selectedPlayerUuid = null;
                refreshUI(ref, store);
            }
            case "promote" -> {
                if (selectedPlayerUuid != null) {
                    boolean success = partyManager.promotePlayer(playerRef.getUuid(), selectedPlayerUuid);
                    if (success) {
                        playSound(ref, store, "SFX_Player_Pickup_Item", 1.0f, 1.2f);
                    }
                    refreshUI(ref, store);
                }
            }
            case "demote" -> {
                if (selectedPlayerUuid != null) {
                    boolean success = partyManager.demotePlayer(playerRef.getUuid(), selectedPlayerUuid);
                    if (success) {
                        playSound(ref, store, "SFX_Player_Drop_Item", 1.0f, 0.8f);
                    }
                    refreshUI(ref, store);
                }
            }
            case "kickSelected" -> {
                if (selectedPlayerUuid != null) {
                    partyManager.kickPlayer(playerRef.getUuid(), selectedPlayerUuid);
                    selectedPlayerUuid = null;
                    refreshUI(ref, store);
                }
            }
            case "confirmTransfer" -> {
                pendingConfirmAction = "transferLeadership";
                refreshUI(ref, store);
            }
            case "accept" -> {
                partyManager.acceptInvite(playerRef.getUuid());
                refreshUI(ref, store);
            }
            case "decline" -> {
                partyManager.declineInvite(playerRef.getUuid());
                refreshUI(ref, store);
            }
            default -> refreshUI(ref, store);
        }
    }

    private void buildContent(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
        UUID playerUuid = playerRef.getUuid();
        Party party = partyManager.getPartyByPlayer(playerUuid);
        PartyInvite invite = partyManager.getPendingInvite(playerUuid);

        // Show pending invite section if there's an invite
        cmd.set("#InviteSection.Visible", invite != null);

        // Handle confirmation view
        if (pendingConfirmAction != null) {
            cmd.set("#NoPartyView.Visible", false);
            cmd.set("#PartyView.Visible", false);
            cmd.set("#InvitePlayersView.Visible", false);
            cmd.set("#PlayerActionView.Visible", false);
            cmd.set("#ConfirmView.Visible", true);

            if ("leave".equals(pendingConfirmAction)) {
                cmd.set("#ConfirmTitle.Text", "Leave Party");
                cmd.set("#ConfirmMessage.Text", "Are you sure you want to leave the party?");
            } else if ("disband".equals(pendingConfirmAction)) {
                cmd.set("#ConfirmTitle.Text", "Disband Party");
                cmd.set("#ConfirmMessage.Text", "Are you sure you want to disband the party?");
            } else if ("transferLeadership".equals(pendingConfirmAction)) {
                PlayerRef selectedRef = selectedPlayerUuid != null ? Universe.get().getPlayer(selectedPlayerUuid) : null;
                String selectedName = selectedRef != null ? selectedRef.getUsername() : "this player";
                cmd.set("#ConfirmTitle.Text", "Transfer Leadership");
                cmd.set("#ConfirmMessage.Text", "Make " + selectedName + " the new party leader?");
            }
            return;
        }

        cmd.set("#ConfirmView.Visible", false);

        if (party != null) {
            // Player is in a party
            cmd.set("#NoPartyView.Visible", false);

            if (selectedPlayerUuid != null) {
                // Show player action view
                cmd.set("#PartyView.Visible", false);
                cmd.set("#InvitePlayersView.Visible", false);
                cmd.set("#PlayerActionView.Visible", true);
                buildPlayerActionView(cmd, events, playerUuid, party);
            } else if (showingInviteView) {
                // Show invite players view
                cmd.set("#PartyView.Visible", false);
                cmd.set("#InvitePlayersView.Visible", true);
                cmd.set("#PlayerActionView.Visible", false);
                buildInvitePlayersView(cmd, events, playerUuid);
            } else {
                // Show party view
                cmd.set("#PartyView.Visible", true);
                cmd.set("#InvitePlayersView.Visible", false);
                cmd.set("#PlayerActionView.Visible", false);
                buildPartyView(cmd, events, playerUuid, party);
            }
        } else {
            // Player is not in a party
            cmd.set("#NoPartyView.Visible", true);
            cmd.set("#PartyView.Visible", false);
            cmd.set("#InvitePlayersView.Visible", false);
            cmd.set("#PlayerActionView.Visible", false);
        }
    }

    private void buildPartyView(@Nonnull UICommandBuilder cmd,
                                @Nonnull UIEventBuilder events,
                                @Nonnull UUID playerUuid,
                                @Nonnull Party party) {
        boolean isLeader = party.isLeader(playerUuid);
        PartyRole playerRole = party.getRole(playerUuid);
        boolean canInvite = isLeader || playerRole.getLevel() >= PartyRole.MEMBER.getLevel();
        int memberCount = party.getMemberUuids().size();

        // Update member count
        cmd.set("#MemberCount.Text", memberCount + "/" + MAX_PARTY_SIZE);

        // Show leader actions if player is leader
        cmd.set("#LeaderActions.Visible", isLeader);

        // Show invite button only for Moderator+ roles
        cmd.set("#InvitePlayerButton.Visible", canInvite);

        // Build member list
        int index = 0;
        for (UUID memberUuid : party.getMemberUuids()) {
            PlayerRef memberRef = Universe.get().getPlayer(memberUuid);
            String name = memberRef != null ? memberRef.getUsername() : "Unknown";

            boolean isOnline = memberRef != null;
            String roleDisplay = party.getRoleDisplayName(memberUuid);
            String status = isOnline ? roleDisplay : roleDisplay + " (Offline)";

            String selector = "#PartyMembersList[" + index + "]";
            cmd.append("#PartyMembersList", "Components/PartyButton.ui");
            cmd.set(selector + " #Name.Text", name);
            cmd.set(selector + " #Subtext.Text", status);
            cmd.set(selector + " #StatusBadge.Visible", isOnline);

            // Leader can click on other members to open action view
            if (isLeader && !memberUuid.equals(playerUuid)) {
                events.addEventBinding(CustomUIEventBindingType.Activating, selector,
                        EventData.of("Action", "selectPlayer").append("Target", memberUuid.toString()), false);
            }

            index++;
        }
    }

    private void buildInvitePlayersView(@Nonnull UICommandBuilder cmd,
                                        @Nonnull UIEventBuilder events,
                                        @Nonnull UUID playerUuid) {
        int index = 0;
        for (PlayerRef player : Universe.get().getPlayers()) {
            UUID otherUuid = player.getUuid();

            // Skip self and players already in a party
            if (otherUuid.equals(playerUuid)) continue;
            if (partyManager.isInParty(otherUuid)) continue;

            String name = player.getUsername();

            String selector = "#PlayersList[" + index + "]";
            cmd.append("#PlayersList", "Components/PartyButton.ui");
            cmd.set(selector + " #Name.Text", name);
            cmd.set(selector + " #Subtext.Text", "Invite");
            cmd.set(selector + " #StatusBadge.Visible", true);

            events.addEventBinding(CustomUIEventBindingType.Activating, selector,
                    EventData.of("Action", "invite").append("Target", otherUuid.toString()), false);

            index++;
        }

        // Show message if no players available
        if (index == 0) {
            cmd.append("#PlayersList", "Components/PartyButton.ui");
            cmd.set("#PlayersList[0] #Name.Text", "No players available");
            cmd.set("#PlayersList[0] #Subtext.Text", "");
        }
    }

    private void buildPlayerActionView(@Nonnull UICommandBuilder cmd,
                                        @Nonnull UIEventBuilder events,
                                        @Nonnull UUID playerUuid,
                                        @Nonnull Party party) {
        if (selectedPlayerUuid == null) return;

        PlayerRef selectedRef = Universe.get().getPlayer(selectedPlayerUuid);
        String selectedName = selectedRef != null ? selectedRef.getUsername() : "Unknown";
        boolean isOnline = selectedRef != null;
        PartyRole currentRole = party.getRole(selectedPlayerUuid);
        String roleDisplay = party.getRoleDisplayName(selectedPlayerUuid);

        // Set player info
        cmd.set("#SelectedPlayerName.Text", selectedName);
        cmd.set("#SelectedPlayerRole.Text", roleDisplay);
        cmd.set("#SelectedPlayerBadge.Visible", isOnline);

        // Promote button - can promote Member to Moderator, Moderator to Admin
        PartyRole nextRole = currentRole.getNextRole();
        if (nextRole != null) {
            cmd.set("#PromoteButton.Visible", true);
            cmd.set("#PromoteButton.Text", "Promote to " + nextRole.getDisplayName());
            cmd.set("#PromoteButton.TooltipText", getRoleDescription(nextRole));
        } else {
            cmd.set("#PromoteButton.Visible", false);
        }

        // Demote button - can demote Admin to Moderator, Moderator to Member
        PartyRole prevRole = currentRole.getPreviousRole();
        if (prevRole != null) {
            cmd.set("#DemoteButton.Visible", true);
            cmd.set("#DemoteButton.Text", "Demote to " + prevRole.getDisplayName());
            cmd.set("#DemoteButton.TooltipText", getRoleDescription(prevRole));
        } else {
            cmd.set("#DemoteButton.Visible", false);
        }

        // Kick button - always visible
        cmd.set("#KickPlayerButton.Visible", true);

        // Transfer leadership button - only if viewer is leader
        cmd.set("#TransferLeadershipButton.Visible", party.isLeader(playerUuid));
    }

    private void refreshUI(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();

        // Clear dynamic content
        cmd.clear("#PartyMembersList");
        cmd.clear("#PlayersList");

        buildContent(cmd, events);
        sendUpdate(cmd, events, false);
    }

    private void closePage(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.getPageManager().setPage(ref, store, Page.None);
        }
    }

    private String getRoleDescription(PartyRole role) {
        return switch (role) {
            case GUEST -> "Guest\n- Basic party guest\n- Can chat with the party";
            case MEMBER -> "Member\n- Can invite players\n- Can chat with the party";
            case MODERATOR -> "Moderator\n- Can kick Guests and Members\n- Can invite players\n- Can chat with the party";
            case ADMIN -> "Admin\n- Can kick Guests, Members, and Moderators\n- Can promote/demote players\n- Can invite players\n- Can chat with the party";
        };
    }

    private void playSound(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                           @Nonnull String soundId, float volume, float pitch) {
        int soundIndex = TempAssetIdUtil.getSoundEventIndex(soundId);
        if (soundIndex > 0) {
            SoundUtil.playSoundEvent2d(ref, soundIndex, SoundCategory.UI, volume, pitch, store);
        }
    }
}
