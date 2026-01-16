package com.gaukh.partymod.ui;

import com.gaukh.partymod.PartyMod;
import com.gaukh.partymod.party.Party;
import com.gaukh.partymod.party.PartyInvite;
import com.gaukh.partymod.party.PartyManager;
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public class PartyMenuUI extends InteractiveCustomUIPage<PartyMenuEventData> {

    private final PartyManager partyManager;

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
        buildContent(cmd, events);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull PartyMenuEventData data) {
        PartyMenuHandler handler = new PartyMenuHandler(partyManager, playerRef);
        boolean shouldRefresh = handler.handle(data.getAction(), data.getTarget());

        if (shouldRefresh) {
            refreshUI(ref, store);
        } else {
            closePage(ref, store);
        }
    }

    private void buildContent(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
        UUID playerUuid = playerRef.getUuid();
        Party party = partyManager.getPartyByPlayer(playerUuid);
        PartyInvite invite = partyManager.getPendingInvite(playerUuid);

        if (invite != null) {
            buildInviteButtons(cmd, events);
        }

        if (party != null) {
            buildPartyView(cmd, events, playerUuid, party);
        } else if (invite == null) {
            buildNoPartyView(cmd, events, playerUuid);
        }
    }

    private void buildInviteButtons(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
        addButton(cmd, "#InviteSection", 0, "Accept", "");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#InviteSection[0]",
                EventData.of("Action", "accept"), false);

        addButton(cmd, "#InviteSection", 1, "Decline", "");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#InviteSection[1]",
                EventData.of("Action", "decline"), false);
    }

    private void buildPartyView(@Nonnull UICommandBuilder cmd,
                                @Nonnull UIEventBuilder events,
                                @Nonnull UUID playerUuid,
                                @Nonnull Party party) {
        boolean isLeader = party.isLeader(playerUuid);
        int index = 0;

        for (UUID memberUuid : party.getMemberUuids()) {
            PlayerRef memberRef = Universe.get().getPlayer(memberUuid);
            String name = memberRef != null ? memberRef.getUsername() : memberUuid.toString().substring(0, 8);
            String status = party.isLeader(memberUuid) ? "[Leader]" : (memberRef == null ? "(Offline)" : "");

            addButton(cmd, "#PartyMembersList", index, name, status);

            if (isLeader && !memberUuid.equals(playerUuid)) {
                events.addEventBinding(CustomUIEventBindingType.Activating,
                        "#PartyMembersList[" + index + "]",
                        EventData.of("Action", "kick").append("Target", memberUuid.toString()), false);
            }
            index++;
        }

        addButton(cmd, "#ActionButtons", 0, "Leave Party", "");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ActionButtons[0]",
                EventData.of("Action", "leave"), false);

        if (isLeader) {
            addButton(cmd, "#ActionButtons", 1, "Disband Party", "");
            events.addEventBinding(CustomUIEventBindingType.Activating, "#ActionButtons[1]",
                    EventData.of("Action", "disband"), false);
        }
    }

    private void buildNoPartyView(@Nonnull UICommandBuilder cmd,
                                  @Nonnull UIEventBuilder events,
                                  @Nonnull UUID playerUuid) {
        addButton(cmd, "#ActionButtons", 0, "Create Party", "");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ActionButtons[0]",
                EventData.of("Action", "create"), false);

        int index = 0;
        for (PlayerRef player : Universe.get().getPlayers()) {
            if (player.getUuid().equals(playerUuid)) continue;
            if (partyManager.isInParty(player.getUuid())) continue;

            addButton(cmd, "#PlayersList", index, player.getUsername(), "Click to invite");
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    "#PlayersList[" + index + "]",
                    EventData.of("Action", "invite").append("Target", player.getUuid().toString()), false);
            index++;
        }

        if (index == 0) {
            addButton(cmd, "#PlayersList", 0, "No players to invite", "");
        }
    }

    private void addButton(@Nonnull UICommandBuilder cmd, String section, int index, String name, String subtext) {
        cmd.append(section, "Pages/WarpEntryButton.ui");
        cmd.set(section + "[" + index + "] #Name.Text", name);
        cmd.set(section + "[" + index + "] #World.Text", subtext);
    }

    private void refreshUI(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();

        cmd.clear("#InviteSection");
        cmd.clear("#PartyMembersList");
        cmd.clear("#ActionButtons");
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
}
