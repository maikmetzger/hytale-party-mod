package com.gaukh.partymod.pages;

import com.gaukh.partymod.PartyMod;
import com.gaukh.partymod.party.*;
import com.gaukh.partymod.ui.PartyMenuEventData;
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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.Message;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class PartyMenuPage extends InteractiveCustomUIPage<PartyMenuEventData> {

    private enum ViewState {
        NO_PARTY_VIEW,
        CREATE_PARTY_VIEW,
        PARTY_VIEW,
        SETTINGS_VIEW,
        INVITE_VIEW,
        PLAYER_ACTION_VIEW,
        INVITES_LIST_VIEW,
        ENTER_PASSWORD_VIEW,
        JOIN_REQUEST_VIEW,
        CONFIRMATION_VIEW
    }

    private enum TabState {
        PARTY,
        SETTINGS
    }

    private final PartyManager partyManager;

    // View state
    private ViewState currentView = ViewState.NO_PARTY_VIEW;
    private TabState currentTab = TabState.PARTY;

    // Input values
    private String inputPartyName = "";
    private String inputPassword = "";
    private PartyAccessType selectedAccessType = PartyAccessType.LOCKED;
    private int selectedMaxMembers = 8;

    // Selection state
    private UUID selectedPlayerUuid = null;
    private String pendingJoinPartyId = null;
    private String pendingConfirmAction = null;

    public PartyMenuPage(@Nonnull PlayerRef playerRef, @Nonnull PartyMod plugin) {
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

        // Tab Navigation
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PartyTab",
                EventData.of("Action", "switchToPartyTab"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SettingsTab",
                EventData.of("Action", "switchToSettingsTab"), false);

        // No Party View
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CreatePartyButton",
                EventData.of("Action", "showCreateParty"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ViewInvitesButton",
                EventData.of("Action", "viewInvites"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RefreshListButton",
                EventData.of("Action", "refreshList"), false);

        // Create Party View
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BackFromCreate",
                EventData.of("Action", "backFromCreate"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmCreateButton",
                EventData.of("Action", "confirmCreate")
                        .append("@PartyName", "#PartyNameInput.Value")
                        .append("@Password", "#CreatePasswordInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CreateAccessOpen",
                EventData.of("Action", "setCreateAccessType").append("Target", "OPEN"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CreateAccessPassword",
                EventData.of("Action", "setCreateAccessType").append("Target", "PASSWORDED"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CreateAccessRequest",
                EventData.of("Action", "setCreateAccessType").append("Target", "REQUEST_ONLY"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CreateAccessLocked",
                EventData.of("Action", "setCreateAccessType").append("Target", "LOCKED"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CreateMaxMembersIncrease",
                EventData.of("Action", "createMaxMembersChange").append("Target", "increase"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CreateMaxMembersDecrease",
                EventData.of("Action", "createMaxMembersChange").append("Target", "decrease"), false);

        // Party View buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#InvitePlayerButton",
                EventData.of("Action", "showInvite"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#LeavePartyButton",
                EventData.of("Action", "leave"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ViewRequestsButton",
                EventData.of("Action", "viewJoinRequests"), false);

        // Settings View - read TextField values when saving
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SaveSettingsButton",
                EventData.of("Action", "saveSettings")
                        .append("@PartyName", "#SettingsNameInput.Value")
                        .append("@Password", "#SettingsPasswordInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DisbandButton",
                EventData.of("Action", "disband"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#AccessOpen",
                EventData.of("Action", "setAccessType").append("Target", "OPEN"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#AccessPassword",
                EventData.of("Action", "setAccessType").append("Target", "PASSWORDED"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#AccessRequest",
                EventData.of("Action", "setAccessType").append("Target", "REQUEST_ONLY"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#AccessLocked",
                EventData.of("Action", "setAccessType").append("Target", "LOCKED"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#MaxMembersIncrease",
                EventData.of("Action", "maxMembersChange").append("Target", "increase"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#MaxMembersDecrease",
                EventData.of("Action", "maxMembersChange").append("Target", "decrease"), false);

        // Invite View
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BackToPartyButton",
                EventData.of("Action", "backToParty"), false);

        // Password Entry View
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BackFromPassword",
                EventData.of("Action", "backFromPassword"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#JoinWithPasswordButton",
                EventData.of("Action", "joinWithPassword")
                        .append("@Password", "#PasswordInput.Value"), false);

        // Invites List View
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BackFromInvites",
                EventData.of("Action", "backFromInvites"), false);

        // Join Requests View
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BackFromRequests",
                EventData.of("Action", "backFromRequests"), false);

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

            // Tab Navigation
            case "switchToPartyTab" -> {
                currentTab = TabState.PARTY;
                currentView = ViewState.PARTY_VIEW;
                refreshUI(ref, store);
            }
            case "switchToSettingsTab" -> {
                currentTab = TabState.SETTINGS;
                currentView = ViewState.SETTINGS_VIEW;
                loadSettingsFromParty();
                refreshUI(ref, store);
            }

            // Create Party Flow
            case "showCreateParty" -> {
                currentView = ViewState.CREATE_PARTY_VIEW;
                // Set defaults for new party
                inputPartyName = playerRef.getUsername() + "'s Party";
                inputPassword = "";
                selectedAccessType = PartyAccessType.OPEN;
                selectedMaxMembers = 8;
                refreshUI(ref, store);
            }
            case "backFromCreate" -> {
                currentView = ViewState.NO_PARTY_VIEW;
                refreshUI(ref, store);
            }
            case "setCreateAccessType" -> {
                if (target != null) {
                    selectedAccessType = PartyAccessType.valueOf(target);
                    refreshUI(ref, store);
                }
            }
            case "createMaxMembersChange" -> {
                selectedMaxMembers = Party.adjustBounded(target, selectedMaxMembers, 1, 20);
                refreshUI(ref, store);
            }
            case "confirmCreate" -> {
                // Read values from event data
                String partyName = data.getPartyName();
                String password = data.getPassword();

                if (partyName != null && !partyName.isBlank()) {
                    inputPartyName = partyName;
                }
                if (password != null) {
                    inputPassword = password;
                }

                if (!inputPartyName.isBlank()) {
                    Party party = partyManager.createParty(playerRef.getUuid(), inputPartyName);
                    if (party != null) {
                        // Only save password if PASSWORDED access type is selected
                        String finalPassword = selectedAccessType == PartyAccessType.PASSWORDED && !inputPassword.isEmpty()
                                ? inputPassword : null;
                        // Apply settings to newly created party
                        partyManager.updatePartySettings(
                                playerRef.getUuid(),
                                inputPartyName,
                                finalPassword,
                                selectedAccessType,
                                selectedMaxMembers
                        );
                    }
                    currentView = ViewState.PARTY_VIEW;
                    currentTab = TabState.PARTY;
                }
                refreshUI(ref, store);
            }

            // Public Party Actions
            case "joinOpenParty" -> {
                if (target != null) {
                    boolean success = partyManager.joinOpenParty(playerRef.getUuid(), target);
                    if (success) {
                        currentView = ViewState.PARTY_VIEW;
                        currentTab = TabState.PARTY;
                    }
                }
                refreshUI(ref, store);
            }
            case "showPasswordEntry" -> {
                if (target != null) {
                    pendingJoinPartyId = target;
                    currentView = ViewState.ENTER_PASSWORD_VIEW;
                    inputPassword = "";
                }
                refreshUI(ref, store);
            }
            case "sendJoinRequest" -> {
                if (target != null) {
                    boolean success = partyManager.sendJoinRequest(playerRef.getUuid(), target);
                    if (success) {
                        playerRef.sendMessage(Message.raw("Join request sent!"));
                    }
                }
                refreshUI(ref, store);
            }

            // Password Entry
            case "backFromPassword" -> {
                currentView = ViewState.NO_PARTY_VIEW;
                pendingJoinPartyId = null;
                refreshUI(ref, store);
            }
            case "joinWithPassword" -> {
                if (pendingJoinPartyId != null) {
                    // Read password from event data
                    String password = data.getPassword();
                    if (password != null) {
                        inputPassword = password;
                    }

                    boolean success = partyManager.joinWithPassword(
                            playerRef.getUuid(), pendingJoinPartyId, inputPassword);
                    if (success) {
                        currentView = ViewState.PARTY_VIEW;
                        currentTab = TabState.PARTY;
                    } else {
                        playerRef.sendMessage(Message.raw("Wrong password!"));
                    }
                    pendingJoinPartyId = null;
                }
                refreshUI(ref, store);
            }

            // Invites View
            case "viewInvites" -> {
                currentView = ViewState.INVITES_LIST_VIEW;
                refreshUI(ref, store);
            }
            case "backFromInvites" -> {
                currentView = ViewState.NO_PARTY_VIEW;
                refreshUI(ref, store);
            }

            // Refresh
            case "refreshList" -> refreshUI(ref, store);

            // Settings Actions
            case "setAccessType" -> {
                if (target != null) {
                    selectedAccessType = PartyAccessType.valueOf(target);
                }
                refreshUI(ref, store);
            }
            case "maxMembersChange" -> {
                selectedMaxMembers = Party.adjustBounded(target, selectedMaxMembers, 1, 20);
                refreshUI(ref, store);
            }
            case "saveSettings" -> {
                // Read TextField values from event data
                String partyName = data.getPartyName();
                String password = data.getPassword();

                if (partyName != null && !partyName.isBlank()) {
                    inputPartyName = partyName;
                }
                if (password != null) {
                    inputPassword = password;
                }

                // Only save password if PASSWORDED access type is selected
                String finalPassword = selectedAccessType == PartyAccessType.PASSWORDED && !inputPassword.isEmpty()
                        ? inputPassword : null;

                partyManager.updatePartySettings(
                        playerRef.getUuid(),
                        inputPartyName,
                        finalPassword,
                        selectedAccessType,
                        selectedMaxMembers
                );
                playerRef.sendMessage(Message.raw("Settings saved!"));
                refreshUI(ref, store);
            }

            // Join Requests (Leader)
            case "viewJoinRequests" -> {
                currentView = ViewState.JOIN_REQUEST_VIEW;
                refreshUI(ref, store);
            }
            case "backFromRequests" -> {
                currentView = ViewState.PARTY_VIEW;
                refreshUI(ref, store);
            }
            case "acceptRequest" -> {
                if (target != null) {
                    partyManager.acceptJoinRequest(playerRef.getUuid(), UUID.fromString(target));
                }
                refreshUI(ref, store);
            }
            case "declineRequest" -> {
                if (target != null) {
                    partyManager.declineJoinRequest(playerRef.getUuid(), UUID.fromString(target));
                }
                refreshUI(ref, store);
            }

            // Party View
            case "showInvite" -> {
                currentView = ViewState.INVITE_VIEW;
                refreshUI(ref, store);
            }
            case "backToParty" -> {
                currentView = ViewState.PARTY_VIEW;
                refreshUI(ref, store);
            }
            case "invite" -> {
                if (target != null) {
                    UUID targetUuid = UUID.fromString(target);
                    partyManager.sendInvite(playerRef.getUuid(), targetUuid);
                    PlayerRef targetRef = Universe.get().getPlayer(targetUuid);
                    if (targetRef != null) {
                        targetRef.sendMessage(Message.raw(
                                playerRef.getUsername() + " invited you to their party!"));
                    }
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
                    currentView = ViewState.NO_PARTY_VIEW;
                    pendingConfirmAction = null;
                    refreshUI(ref, store);
                } else if ("disband".equals(pendingConfirmAction)) {
                    Party party = partyManager.getPartyByPlayer(playerRef.getUuid());
                    if (party != null) {
                        partyManager.disbandParty(party.getId());
                    }
                    currentView = ViewState.NO_PARTY_VIEW;
                    pendingConfirmAction = null;
                    refreshUI(ref, store);
                } else if ("transferLeadership".equals(pendingConfirmAction) && selectedPlayerUuid != null) {
                    partyManager.transferLeadership(playerRef.getUuid(), selectedPlayerUuid);
                    selectedPlayerUuid = null;
                    pendingConfirmAction = null;
                    currentView = ViewState.PARTY_VIEW;
                    refreshUI(ref, store);
                }
            }

            // Player Action View
            case "selectPlayer" -> {
                if (target != null) {
                    selectedPlayerUuid = UUID.fromString(target);
                    currentView = ViewState.PLAYER_ACTION_VIEW;
                    refreshUI(ref, store);
                }
            }
            case "backToPartyFromAction" -> {
                selectedPlayerUuid = null;
                currentView = ViewState.PARTY_VIEW;
                refreshUI(ref, store);
            }
            case "promote" -> {
                if (selectedPlayerUuid != null) {
                    partyManager.promotePlayer(playerRef.getUuid(), selectedPlayerUuid);
                    refreshUI(ref, store);
                }
            }
            case "demote" -> {
                if (selectedPlayerUuid != null) {
                    partyManager.demotePlayer(playerRef.getUuid(), selectedPlayerUuid);
                    refreshUI(ref, store);
                }
            }
            case "kickSelected" -> {
                if (selectedPlayerUuid != null) {
                    partyManager.kickPlayer(playerRef.getUuid(), selectedPlayerUuid);
                    selectedPlayerUuid = null;
                    currentView = ViewState.PARTY_VIEW;
                    refreshUI(ref, store);
                }
            }
            case "confirmTransfer" -> {
                pendingConfirmAction = "transferLeadership";
                refreshUI(ref, store);
            }

            // Pending Invite
            case "accept" -> {
                partyManager.acceptInvite(playerRef.getUuid());
                currentView = ViewState.PARTY_VIEW;
                currentTab = TabState.PARTY;
                refreshUI(ref, store);
            }
            case "decline" -> {
                partyManager.declineInvite(playerRef.getUuid());
                refreshUI(ref, store);
            }

            default -> refreshUI(ref, store);
        }
    }

    private void loadSettingsFromParty() {
        Party party = partyManager.getPartyByPlayer(playerRef.getUuid());
        if (party != null) {
            inputPartyName = party.getName();
            inputPassword = party.getPassword() != null ? party.getPassword() : "";
            selectedAccessType = party.getAccessType();
            selectedMaxMembers = party.getMaxMembers();
        }
    }

    private void buildContent(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
        UUID playerUuid = playerRef.getUuid();
        Party party = partyManager.getPartyByPlayer(playerUuid);
        PartyInvite invite = partyManager.getPendingInvite(playerUuid);

        // Hide all views first
        hideAllViews(cmd);

        // Show pending invite section if there's an invite (but not in invites list view)
        cmd.set("#InviteSection.Visible", invite != null && currentView != ViewState.INVITES_LIST_VIEW);

        // Tab bar visibility and state
        boolean isInParty = party != null;
        boolean isLeader = party != null && party.isLeader(playerUuid);

        cmd.set("#TabBar.Visible", isInParty && currentView != ViewState.CONFIRMATION_VIEW);
        cmd.set("#SettingsTab.Visible", isLeader);

        // Set selected tab
        if (isInParty) {
            String selectedTab = currentTab == TabState.PARTY ? "Party" : "Settings";
            cmd.set("#TabBar.SelectedTab", selectedTab);
        }

        // Handle confirmation view
        if (pendingConfirmAction != null) {
            buildConfirmView(cmd);
            return;
        }

        // Determine view based on party membership
        if (!isInParty) {
            // If not in party, reset party-related views to NO_PARTY
            if (currentView == ViewState.PARTY_VIEW || currentView == ViewState.SETTINGS_VIEW ||
                currentView == ViewState.INVITE_VIEW || currentView == ViewState.PLAYER_ACTION_VIEW ||
                currentView == ViewState.JOIN_REQUEST_VIEW) {
                currentView = ViewState.NO_PARTY_VIEW;
            }
        } else {
            // If in party, don't show NO_PARTY or CREATE_PARTY views
            if (currentView == ViewState.NO_PARTY_VIEW || currentView == ViewState.CREATE_PARTY_VIEW ||
                currentView == ViewState.INVITES_LIST_VIEW || currentView == ViewState.ENTER_PASSWORD_VIEW) {
                currentView = ViewState.PARTY_VIEW;
            }
        }

        // Build appropriate view
        switch (currentView) {
            case NO_PARTY_VIEW -> buildNoPartyView(cmd, events);
            case CREATE_PARTY_VIEW -> buildCreatePartyView(cmd, events);
            case PARTY_VIEW -> buildPartyView(cmd, events, playerUuid, party);
            case SETTINGS_VIEW -> buildSettingsView(cmd, events, party);
            case INVITE_VIEW -> buildInvitePlayersView(cmd, events, playerUuid);
            case PLAYER_ACTION_VIEW -> buildPlayerActionView(cmd, events, playerUuid, party);
            case INVITES_LIST_VIEW -> buildInvitesListView(cmd, events, playerUuid);
            case ENTER_PASSWORD_VIEW -> buildPasswordEntryView(cmd, events);
            case JOIN_REQUEST_VIEW -> buildJoinRequestsView(cmd, events, party);
        }
    }

    private void hideAllViews(@Nonnull UICommandBuilder cmd) {
        cmd.set("#NoPartyView.Visible", false);
        cmd.set("#CreatePartyView.Visible", false);
        cmd.set("#PartyView.Visible", false);
        cmd.set("#SettingsView.Visible", false);
        cmd.set("#InvitePlayersView.Visible", false);
        cmd.set("#PlayerActionView.Visible", false);
        cmd.set("#InvitesListView.Visible", false);
        cmd.set("#PasswordEntryView.Visible", false);
        cmd.set("#JoinRequestsView.Visible", false);
        cmd.set("#ConfirmView.Visible", false);
    }

    private void buildConfirmView(@Nonnull UICommandBuilder cmd) {
        cmd.set("#ConfirmView.Visible", true);

        if ("leave".equals(pendingConfirmAction)) {
            cmd.set("#ConfirmTitle.Text", "Leave Party");
            cmd.set("#ConfirmMessage.Text", "Are you sure you want to leave the party?");
        } else if ("disband".equals(pendingConfirmAction)) {
            cmd.set("#ConfirmTitle.Text", "Disband Party");
            cmd.set("#ConfirmMessage.Text", "Are you sure you want to disband the party?");
        } else if ("transferLeadership".equals(pendingConfirmAction)) {
            String selectedName = selectedPlayerUuid != null ? Party.getPlayerName(selectedPlayerUuid) : "this player";
            cmd.set("#ConfirmTitle.Text", "Transfer Leadership");
            cmd.set("#ConfirmMessage.Text", "Make " + selectedName + " the new party leader?");
        }
    }

    private void buildNoPartyView(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
        cmd.set("#NoPartyView.Visible", true);

        // Build public parties list
        List<Party> publicParties = partyManager.getPublicParties();
        int index = 0;

        for (Party party : publicParties) {
            // Skip if player already has a pending request
            if (partyManager.hasPendingJoinRequest(playerRef.getUuid(), party.getId())) {
                continue;
            }

            String selector = "#PublicPartiesList[" + index + "]";
            cmd.append("#PublicPartiesList", "Components/PublicPartyButton.ui");

            cmd.set(selector + " #PartyName.Text", party.getName());

            String leaderName = Party.getPlayerName(party.getLeaderUuid());
            cmd.set(selector + " #PartyLeader.Text", "Leader: " + leaderName);
            cmd.set(selector + " #MemberCount.Text", party.getMemberCount() + "/" + party.getMaxMembers());

            // Access type indicator - show/hide based on type
            cmd.set(selector + " #AccessIcon.Visible", party.getAccessType() != PartyAccessType.OPEN);

            // Event Binding based on access type
            String eventAction = switch (party.getAccessType()) {
                case OPEN -> "joinOpenParty";
                case PASSWORDED -> "showPasswordEntry";
                case REQUEST_ONLY -> "sendJoinRequest";
                case LOCKED -> "none";
            };

            if (!eventAction.equals("none")) {
                events.addEventBinding(CustomUIEventBindingType.Activating, selector,
                        EventData.of("Action", eventAction).append("Target", party.getId()), false);
            }

            index++;
        }

        // Show "No parties available" only when list is empty
        cmd.set("#NoPartiesLabel.Visible", index == 0);

        // Invites count
        PartyInvite invite = partyManager.getPendingInvite(playerRef.getUuid());
        int inviteCount = invite != null ? 1 : 0;
        cmd.set("#ViewInvitesButton.Text", "Invites (" + inviteCount + ")");
    }

    private void buildCreatePartyView(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
        cmd.set("#CreatePartyView.Visible", true);

        // Set access type checkmarks - show only the selected one
        cmd.set("#CreateAccessOpenCheck.Visible", selectedAccessType == PartyAccessType.OPEN);
        cmd.set("#CreateAccessPasswordCheck.Visible", selectedAccessType == PartyAccessType.PASSWORDED);
        cmd.set("#CreateAccessRequestCheck.Visible", selectedAccessType == PartyAccessType.REQUEST_ONLY);
        cmd.set("#CreateAccessLockedCheck.Visible", selectedAccessType == PartyAccessType.LOCKED);

        // Show password field only when PASSWORDED is selected
        cmd.set("#CreatePasswordGroup.Visible", selectedAccessType == PartyAccessType.PASSWORDED);

        // Set max members value
        cmd.set("#CreateMaxMembersValue.Text", String.valueOf(selectedMaxMembers));
    }

    private void buildPartyView(@Nonnull UICommandBuilder cmd,
                                @Nonnull UIEventBuilder events,
                                @Nonnull UUID playerUuid,
                                @Nonnull Party party) {
        cmd.set("#PartyView.Visible", true);

        boolean isLeader = party.isLeader(playerUuid);
        PartyRole playerRole = party.getRole(playerUuid);
        boolean canInvite = isLeader || playerRole.getLevel() >= PartyRole.MEMBER.getLevel();
        int memberCount = party.getMemberUuids().size();

        // Party name
        cmd.set("#PartyNameTitle.Text", party.getName());

        // Update member count
        cmd.set("#MemberCount.Text", memberCount + "/" + party.getMaxMembers());

        // Show invite button only for Member+ roles
        cmd.set("#InvitePlayerButton.Visible", canInvite);

        // Join Requests Button (only for leader with REQUEST_ONLY access)
        List<PartyJoinRequest> requests = partyManager.getJoinRequests(party.getId());
        boolean showRequests = isLeader && party.getAccessType() == PartyAccessType.REQUEST_ONLY && !requests.isEmpty();
        cmd.set("#ViewRequestsButton.Visible", showRequests);
        if (showRequests) {
            cmd.set("#ViewRequestsButton.Text", "Requests (" + requests.size() + ")");
        }

        // Build member list
        int index = 0;
        for (UUID memberUuid : party.getMemberUuids()) {
            String name = party.getMemberName(memberUuid);
            String status = party.getMemberStatus(memberUuid);
            boolean isOnline = Party.isPlayerOnline(memberUuid);

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

    private void buildSettingsView(@Nonnull UICommandBuilder cmd,
                                   @Nonnull UIEventBuilder events,
                                   @Nullable Party party) {
        if (party == null) return;

        cmd.set("#SettingsView.Visible", true);

        // Load current values from party if not already loaded
        if (inputPartyName.isEmpty() || !inputPartyName.equals(party.getName())) {
            inputPartyName = party.getName();
            inputPassword = party.getPassword() != null ? party.getPassword() : "";
            selectedAccessType = party.getAccessType();
            selectedMaxMembers = party.getMaxMembers();
        }

        // Set TextField values from current state
        cmd.set("#SettingsNameInput.Value", inputPartyName);
        cmd.set("#SettingsPasswordInput.Value", inputPassword != null ? inputPassword : "");

        // Access Type checkmarks - show only the selected one
        cmd.set("#AccessOpenCheck.Visible", selectedAccessType == PartyAccessType.OPEN);
        cmd.set("#AccessPasswordCheck.Visible", selectedAccessType == PartyAccessType.PASSWORDED);
        cmd.set("#AccessRequestCheck.Visible", selectedAccessType == PartyAccessType.REQUEST_ONLY);
        cmd.set("#AccessLockedCheck.Visible", selectedAccessType == PartyAccessType.LOCKED);

        // Show password field only when PASSWORDED is selected
        cmd.set("#SettingsPasswordGroup.Visible", selectedAccessType == PartyAccessType.PASSWORDED);

        // Max Members
        cmd.set("#MaxMembersValue.Text", String.valueOf(selectedMaxMembers));
    }

    private void buildInvitePlayersView(@Nonnull UICommandBuilder cmd,
                                        @Nonnull UIEventBuilder events,
                                        @Nonnull UUID playerUuid) {
        cmd.set("#InvitePlayersView.Visible", true);

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

        if (index == 0) {
            cmd.append("#PlayersList", "Components/PartyButton.ui");
            cmd.set("#PlayersList[0] #Name.Text", "No players available");
            cmd.set("#PlayersList[0] #Subtext.Text", "");
        }
    }

    private void buildPlayerActionView(@Nonnull UICommandBuilder cmd,
                                       @Nonnull UIEventBuilder events,
                                       @Nonnull UUID playerUuid,
                                       @Nullable Party party) {
        if (selectedPlayerUuid == null || party == null) return;

        cmd.set("#PlayerActionView.Visible", true);

        String selectedName = party.getMemberName(selectedPlayerUuid);
        boolean isOnline = Party.isPlayerOnline(selectedPlayerUuid);
        PartyRole currentRole = party.getRole(selectedPlayerUuid);
        String roleDisplay = party.getRoleDisplayName(selectedPlayerUuid);

        // Set player info
        cmd.set("#SelectedPlayerName.Text", selectedName);
        cmd.set("#SelectedPlayerRole.Text", roleDisplay);
        cmd.set("#SelectedPlayerBadge.Visible", isOnline);

        // Promote button
        PartyRole nextRole = currentRole.getNextRole();
        if (nextRole != null) {
            cmd.set("#PromoteButton.Visible", true);
            cmd.set("#PromoteButton.Text", "Promote to " + nextRole.getDisplayName());
        } else {
            cmd.set("#PromoteButton.Visible", false);
        }

        // Demote button
        PartyRole prevRole = currentRole.getPreviousRole();
        if (prevRole != null) {
            cmd.set("#DemoteButton.Visible", true);
            cmd.set("#DemoteButton.Text", "Demote to " + prevRole.getDisplayName());
        } else {
            cmd.set("#DemoteButton.Visible", false);
        }

        // Kick and transfer buttons
        cmd.set("#KickPlayerButton.Visible", true);
        cmd.set("#TransferLeadershipButton.Visible", party.isLeader(playerUuid));
    }

    private void buildInvitesListView(@Nonnull UICommandBuilder cmd,
                                      @Nonnull UIEventBuilder events,
                                      @Nonnull UUID playerUuid) {
        cmd.set("#InvitesListView.Visible", true);

        PartyInvite invite = partyManager.getPendingInvite(playerUuid);
        if (invite != null) {
            String selector = "#InvitesList[0]";
            cmd.append("#InvitesList", "Components/InviteEntryButton.ui");

            Party party = partyManager.getPartyById(invite.getPartyId());
            String partyName = party != null ? party.getName() : "Unknown Party";

            String inviterName = Party.getPlayerName(invite.getInviterUuid());

            cmd.set(selector + " #PartyName.Text", partyName);
            cmd.set(selector + " #InviterName.Text", "Invited by: " + inviterName);

            events.addEventBinding(CustomUIEventBindingType.Activating, selector + " #QuickAccept",
                    EventData.of("Action", "accept"), false);
            events.addEventBinding(CustomUIEventBindingType.Activating, selector + " #QuickDecline",
                    EventData.of("Action", "decline"), false);
        } else {
            cmd.append("#InvitesList", "Components/PartyButton.ui");
            cmd.set("#InvitesList[0] #Name.Text", "No invites");
            cmd.set("#InvitesList[0] #Subtext.Text", "");
        }
    }

    private void buildPasswordEntryView(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
        cmd.set("#PasswordEntryView.Visible", true);

        if (pendingJoinPartyId != null) {
            Party party = partyManager.getPartyById(pendingJoinPartyId);
            String partyName = party != null ? party.getName() : "Party";
            cmd.set("#PasswordPartyName.Text", partyName);
        }
        // TextField text is entered by user, not set programmatically
    }

    private void buildJoinRequestsView(@Nonnull UICommandBuilder cmd,
                                       @Nonnull UIEventBuilder events,
                                       @Nullable Party party) {
        if (party == null) return;

        cmd.set("#JoinRequestsView.Visible", true);

        List<PartyJoinRequest> requests = partyManager.getJoinRequests(party.getId());
        int index = 0;

        for (PartyJoinRequest request : requests) {
            String selector = "#RequestsList[" + index + "]";
            cmd.append("#RequestsList", "Components/RequestEntryButton.ui");

            String name = Party.getPlayerName(request.getRequesterUuid());
            cmd.set(selector + " #RequesterName.Text", name);

            events.addEventBinding(CustomUIEventBindingType.Activating, selector + " #AcceptRequest",
                    EventData.of("Action", "acceptRequest").append("Target", request.getRequesterUuid().toString()), false);
            events.addEventBinding(CustomUIEventBindingType.Activating, selector + " #DeclineRequest",
                    EventData.of("Action", "declineRequest").append("Target", request.getRequesterUuid().toString()), false);

            index++;
        }

        if (index == 0) {
            cmd.append("#RequestsList", "Components/PartyButton.ui");
            cmd.set("#RequestsList[0] #Name.Text", "No pending requests");
            cmd.set("#RequestsList[0] #Subtext.Text", "");
        }
    }

    private void refreshUI(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();

        // Clear dynamic content
        cmd.clear("#PartyMembersList");
        cmd.clear("#PlayersList");
        cmd.clear("#PublicPartiesList");
        cmd.clear("#InvitesList");
        cmd.clear("#RequestsList");

        buildContent(cmd, events);
        sendUpdate(cmd, events, false);
    }

    private void closePage(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.getPageManager().setPage(ref, store, Page.None);
        }
    }
}
