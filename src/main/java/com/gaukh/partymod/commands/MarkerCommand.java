package com.gaukh.partymod.commands;

import com.gaukh.partymod.PartyMod;
import com.gaukh.partymod.marker.Marker;
import com.gaukh.partymod.marker.MarkerColor;
import com.gaukh.partymod.marker.MarkerManager;
import com.gaukh.partymod.marker.MarkerVisibility;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

/**
 * Marker command for managing map markers.
 */
public class MarkerCommand extends AbstractPlayerCommand {

    private final PartyMod plugin;
    private final MarkerManager markerManager;

    public MarkerCommand(@Nonnull PartyMod plugin) {
        super("marker", "Map marker management commands");
        this.plugin = plugin;
        this.markerManager = plugin.getMarkerManager();
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
        // Format: "/marker [subcommand] [args...]"
        String input = context.getInputString().trim();
        String[] parts = input.split("\\s+");

        // parts[0] is "marker" (the command name)
        String subcommand = parts.length > 1 ? parts[1] : null;

        if (subcommand == null) {
            sendUsage(context);
            return;
        }

        switch (subcommand.toLowerCase()) {
            case "create" -> handleCreate(context, store, ref, playerRef, playerUuid, parts);
            case "delete" -> handleDelete(context, playerRef, playerUuid, parts);
            case "list" -> handleList(context, playerRef, playerUuid);
            case "share" -> handleShare(context, playerRef, playerUuid, parts);
            case "unshare" -> handleUnshare(context, playerRef, playerUuid, parts);
            default -> sendUsage(context);
        }
    }

    private void sendUsage(@Nonnull CommandContext context) {
        context.sendMessage(Message.raw(
                "Usage:\n" +
                "/marker create <name> [color] - Create a marker\n" +
                "/marker delete <name> - Delete a marker\n" +
                "/marker list - List your markers\n" +
                "/marker share <name> party|global - Share a marker\n" +
                "/marker unshare <name> - Make marker private"
        ));
    }

    private void handleCreate(@Nonnull CommandContext context,
                              @Nonnull Store<EntityStore> store,
                              @Nonnull Ref<EntityStore> ref,
                              @Nonnull PlayerRef playerRef,
                              @Nonnull UUID playerUuid,
                              @Nonnull String[] parts) {
        if (parts.length < 3) {
            context.sendMessage(Message.raw("Usage: /marker create <name> [color]"));
            return;
        }

        String name = parts[2];

        if (markerManager.findMarkerByName(playerUuid, name) != null) {
            context.sendMessage(Message.raw("A marker with that name already exists."));
            return;
        }

        String colorName = parts.length > 3 ? parts[3] : plugin.getPluginConfig().getDefaultMarkerColor();
        MarkerColor color = MarkerColor.fromNameOrDefault(colorName, MarkerColor.BLUE);

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());

        if (transform == null) {
            context.sendMessage(Message.raw("Could not get your position."));
            return;
        }

        double x = transform.getPosition().getX();
        double y = transform.getPosition().getY();
        double z = transform.getPosition().getZ();

        Marker marker = new Marker(name, playerUuid, x, y, z, color, MarkerVisibility.PRIVATE);
        markerManager.createMarker(marker);

        context.sendMessage(Message.raw("Marker '" + name + "' created at " + (int)x + ", " + (int)y + ", " + (int)z));
    }

    private void handleDelete(@Nonnull CommandContext context,
                              @Nonnull PlayerRef playerRef,
                              @Nonnull UUID playerUuid,
                              @Nonnull String[] parts) {
        if (parts.length < 3) {
            context.sendMessage(Message.raw("Usage: /marker delete <name>"));
            return;
        }

        String name = parts[2];

        Marker marker = markerManager.findMarkerByName(playerUuid, name);
        if (marker == null) {
            context.sendMessage(Message.raw("Marker not found."));
            return;
        }

        markerManager.deleteMarker(marker.getId());
        context.sendMessage(Message.raw("Marker '" + name + "' deleted."));
    }

    private void handleList(@Nonnull CommandContext context,
                            @Nonnull PlayerRef playerRef,
                            @Nonnull UUID playerUuid) {
        List<Marker> markers = markerManager.getMarkersByOwner(playerUuid);

        if (markers.isEmpty()) {
            context.sendMessage(Message.raw("You have no markers."));
            return;
        }

        StringBuilder sb = new StringBuilder("Your Markers:\n");
        for (Marker marker : markers) {
            String visibility = switch (marker.getVisibility()) {
                case PRIVATE -> "[Private]";
                case PARTY -> "[Party]";
                case GLOBAL -> "[Global]";
            };
            sb.append(String.format("- %s %s (%s) at %.0f, %.0f, %.0f\n",
                    marker.getName(),
                    visibility,
                    marker.getColor().name(),
                    marker.getX(), marker.getY(), marker.getZ()));
        }

        context.sendMessage(Message.raw(sb.toString()));
    }

    private void handleShare(@Nonnull CommandContext context,
                             @Nonnull PlayerRef playerRef,
                             @Nonnull UUID playerUuid,
                             @Nonnull String[] parts) {
        if (parts.length < 4) {
            context.sendMessage(Message.raw("Usage: /marker share <name> party|global"));
            return;
        }

        String name = parts[2];
        String visibilityStr = parts[3];

        Marker marker = markerManager.findMarkerByName(playerUuid, name);
        if (marker == null) {
            context.sendMessage(Message.raw("Marker not found."));
            return;
        }

        MarkerVisibility visibility;
        switch (visibilityStr.toLowerCase()) {
            case "party" -> visibility = MarkerVisibility.PARTY;
            case "global" -> visibility = MarkerVisibility.GLOBAL;
            default -> {
                context.sendMessage(Message.raw("Visibility must be 'party' or 'global'."));
                return;
            }
        }

        marker.setVisibility(visibility);
        markerManager.updateMarker(marker);

        String message = visibility == MarkerVisibility.PARTY
                ? "Marker shared with party."
                : "Marker shared globally.";
        context.sendMessage(Message.raw(message));
    }

    private void handleUnshare(@Nonnull CommandContext context,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull UUID playerUuid,
                               @Nonnull String[] parts) {
        if (parts.length < 3) {
            context.sendMessage(Message.raw("Usage: /marker unshare <name>"));
            return;
        }

        String name = parts[2];

        Marker marker = markerManager.findMarkerByName(playerUuid, name);
        if (marker == null) {
            context.sendMessage(Message.raw("Marker not found."));
            return;
        }

        marker.setVisibility(MarkerVisibility.PRIVATE);
        markerManager.updateMarker(marker);

        context.sendMessage(Message.raw("Marker is now private."));
    }
}
