package com.gaukh.partymod.ui;

import com.gaukh.partymod.PartyMod;
import com.gaukh.partymod.party.Party;
import com.gaukh.partymod.party.PartyInvite;
import com.gaukh.partymod.party.PartyManager;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Interactive Party Menu UI.
 * Uses Hytale's built-in WarpListPage.ui as a base template.
 */
public class PartyMenuUI extends InteractiveCustomUIPage<PartyMenuUI.PartyMenuEventData> {

    private final PartyMod plugin;
    private final PartyManager partyManager;

    public PartyMenuUI(@Nonnull PlayerRef playerRef, @Nonnull PartyMod plugin) {
        super(playerRef, CustomPageLifetime.CanDismiss, PartyMenuEventData.CODEC);
        this.plugin = plugin;
        this.partyManager = plugin.getPartyManager();
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder,
                      @Nonnull Store<EntityStore> store) {

        UUID playerUuid = playerRef.getUuid();
        Party party = partyManager.getPartyByPlayer(playerUuid);
        PartyInvite pendingInvite = partyManager.getPendingInvite(playerUuid);

        // Use the built-in WarpListPage.ui as base (it's a simple list page)
        commandBuilder.append("Pages/WarpListPage.ui");

        // Change title
        commandBuilder.set("#Title.Text", "Party Menu");

        // Clear the warp list and repurpose it for our party content
        commandBuilder.clear("#WarpList");

        // Hide search input since we don't need it
        commandBuilder.set("#SearchInput.Visible", false);

        buildContent(commandBuilder, eventBuilder, playerUuid, party, pendingInvite);
    }

    private void buildContent(@Nonnull UICommandBuilder commandBuilder,
                              @Nonnull UIEventBuilder eventBuilder,
                              @Nonnull UUID playerUuid,
                              @Nullable Party party,
                              @Nullable PartyInvite pendingInvite) {

        int index = 0;

        // Pending invite section
        if (pendingInvite != null) {
            PlayerRef inviterRef = Universe.get().getPlayer(pendingInvite.getInviterUuid());
            String inviterName = inviterRef != null ? inviterRef.getUsername() : "Unknown";

            commandBuilder.appendInline("#WarpList", "Label { Text: \"Invite from: " + inviterName + "\"; Style: (TextColor: #ffff00; RenderBold: true); }");
            index++;

            // Accept button
            commandBuilder.append("#WarpList", "Pages/WarpEntryButton.ui");
            commandBuilder.set("#WarpList[" + index + "] #Name.Text", "Accept");
            commandBuilder.set("#WarpList[" + index + "] #World.Text", "");
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#WarpList[" + index + "]",
                    EventData.of("Action", "accept"), false);
            index++;

            // Decline button
            commandBuilder.append("#WarpList", "Pages/WarpEntryButton.ui");
            commandBuilder.set("#WarpList[" + index + "] #Name.Text", "Decline");
            commandBuilder.set("#WarpList[" + index + "] #World.Text", "");
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#WarpList[" + index + "]",
                    EventData.of("Action", "decline"), false);
            index++;

            // Separator
            commandBuilder.appendInline("#WarpList", "Label { Text: \" \"; }");
            index++;
        }

        if (party != null) {
            // Party members header
            commandBuilder.appendInline("#WarpList", "Label { Text: \"Party Members:\"; Style: (TextColor: #96a9be; RenderUppercase: true); }");
            index++;

            // List members
            for (UUID memberUuid : party.getMemberUuids()) {
                PlayerRef memberRef = Universe.get().getPlayer(memberUuid);
                String memberName = memberRef != null ? memberRef.getUsername() : memberUuid.toString().substring(0, 8);
                String status = party.isLeader(memberUuid) ? "[Leader]" : (memberRef == null ? "(Offline)" : "");

                commandBuilder.append("#WarpList", "Pages/WarpEntryButton.ui");
                commandBuilder.set("#WarpList[" + index + "] #Name.Text", memberName);
                commandBuilder.set("#WarpList[" + index + "] #World.Text", status);

                // Kick button for leader (not for self)
                if (party.isLeader(playerUuid) && !memberUuid.equals(playerUuid)) {
                    eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#WarpList[" + index + "]",
                            EventData.of("Action", "kick").append("Target", memberUuid.toString()), false);
                }
                index++;
            }

            // Separator
            commandBuilder.appendInline("#WarpList", "Label { Text: \" \"; }");
            index++;

            // Leave button
            commandBuilder.append("#WarpList", "Pages/WarpEntryButton.ui");
            commandBuilder.set("#WarpList[" + index + "] #Name.Text", "Leave Party");
            commandBuilder.set("#WarpList[" + index + "] #World.Text", "");
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#WarpList[" + index + "]",
                    EventData.of("Action", "leave"), false);
            index++;

            // Disband button (leader only)
            if (party.isLeader(playerUuid)) {
                commandBuilder.append("#WarpList", "Pages/WarpEntryButton.ui");
                commandBuilder.set("#WarpList[" + index + "] #Name.Text", "Disband Party");
                commandBuilder.set("#WarpList[" + index + "] #World.Text", "");
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#WarpList[" + index + "]",
                        EventData.of("Action", "disband"), false);
                index++;
            }

        } else if (pendingInvite == null) {
            // Not in party - show create option and online players
            commandBuilder.appendInline("#WarpList", "Label { Text: \"You are not in a party.\"; Style: (TextColor: #888888); }");
            index++;

            commandBuilder.appendInline("#WarpList", "Label { Text: \" \"; }");
            index++;

            // Create party button
            commandBuilder.append("#WarpList", "Pages/WarpEntryButton.ui");
            commandBuilder.set("#WarpList[" + index + "] #Name.Text", "Create Party");
            commandBuilder.set("#WarpList[" + index + "] #World.Text", "");
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#WarpList[" + index + "]",
                    EventData.of("Action", "create"), false);
            index++;

            commandBuilder.appendInline("#WarpList", "Label { Text: \" \"; }");
            index++;

            // Online players header
            commandBuilder.appendInline("#WarpList", "Label { Text: \"Invite Players:\"; Style: (TextColor: #96a9be; RenderUppercase: true); }");
            index++;

            // List online players
            boolean anyPlayers = false;
            for (PlayerRef otherPlayer : Universe.get().getPlayers()) {
                if (otherPlayer.getUuid().equals(playerUuid)) continue;
                if (partyManager.isInParty(otherPlayer.getUuid())) continue;

                commandBuilder.append("#WarpList", "Pages/WarpEntryButton.ui");
                commandBuilder.set("#WarpList[" + index + "] #Name.Text", otherPlayer.getUsername());
                commandBuilder.set("#WarpList[" + index + "] #World.Text", "Invite");
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#WarpList[" + index + "]",
                        EventData.of("Action", "invite").append("Target", otherPlayer.getUuid().toString()), false);
                index++;
                anyPlayers = true;
            }

            if (!anyPlayers) {
                commandBuilder.appendInline("#WarpList", "Label { Text: \"No other players online\"; Style: (TextColor: #666666; Alignment: Center); }");
            }
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull PartyMenuEventData data) {

        UUID playerUuid = playerRef.getUuid();
        String action = data.getAction();
        String target = data.getTarget();

        switch (action) {
            case "create" -> {
                Party party = partyManager.createParty(playerUuid);
                if (party != null) {
                    playerRef.sendMessage(Message.raw("Party created!"));
                }
                refreshUI(ref, store);
            }

            case "accept" -> {
                Party party = partyManager.acceptInvite(playerUuid);
                if (party != null) {
                    playerRef.sendMessage(Message.raw("You joined the party!"));
                } else {
                    playerRef.sendMessage(Message.raw("The invitation has expired."));
                }
                refreshUI(ref, store);
            }

            case "decline" -> {
                partyManager.declineInvite(playerUuid);
                playerRef.sendMessage(Message.raw("You declined the invitation."));
                refreshUI(ref, store);
            }

            case "invite" -> {
                if (target != null) {
                    UUID targetUuid = UUID.fromString(target);
                    PlayerRef targetRef = Universe.get().getPlayer(targetUuid);
                    if (targetRef != null) {
                        boolean sent = partyManager.sendInvite(playerUuid, targetUuid);
                        if (sent) {
                            playerRef.sendMessage(Message.raw("Invitation sent to " + targetRef.getUsername()));
                            targetRef.sendMessage(Message.raw(playerRef.getUsername() + " has invited you to their party. Use /party accept to join."));
                        }
                    }
                }
                refreshUI(ref, store);
            }

            case "kick" -> {
                if (target != null) {
                    UUID targetUuid = UUID.fromString(target);
                    partyManager.kickPlayer(playerUuid, targetUuid);
                }
                refreshUI(ref, store);
            }

            case "leave" -> {
                partyManager.leaveParty(playerUuid, false);
                playerRef.sendMessage(Message.raw("You left the party."));
                closePage(ref, store);
            }

            case "disband" -> {
                Party party = partyManager.getPartyByPlayer(playerUuid);
                if (party != null) {
                    partyManager.disbandParty(party.getId());
                    playerRef.sendMessage(Message.raw("Party disbanded."));
                }
                closePage(ref, store);
            }
        }
    }

    private void closePage(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent != null) {
            playerComponent.getPageManager().setPage(ref, store, Page.None);
        }
    }

    private void refreshUI(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUID playerUuid = playerRef.getUuid();
        Party party = partyManager.getPartyByPlayer(playerUuid);
        PartyInvite pendingInvite = partyManager.getPendingInvite(playerUuid);

        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        commandBuilder.clear("#WarpList");
        buildContent(commandBuilder, eventBuilder, playerUuid, party, pendingInvite);
        sendUpdate(commandBuilder, eventBuilder, false);
    }

    /**
     * Event data codec for PartyMenu events.
     */
    public static class PartyMenuEventData {
        public static final BuilderCodec<PartyMenuEventData> CODEC = BuilderCodec.builder(
                PartyMenuEventData.class,
                PartyMenuEventData::new
        ).append(
                new KeyedCodec<>("Action", Codec.STRING),
                (data, value) -> data.action = value,
                data -> data.action
        ).add().append(
                new KeyedCodec<>("Target", Codec.STRING),
                (data, value) -> data.target = value,
                data -> data.target
        ).add().build();

        private String action;
        private String target;

        @Nonnull
        public String getAction() {
            return action != null ? action : "";
        }

        @Nullable
        public String getTarget() {
            return target;
        }
    }
}
