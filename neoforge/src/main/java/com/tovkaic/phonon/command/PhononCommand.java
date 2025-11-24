package com.tovkaic.phonon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.tovkaic.phonon.audio.AudioManager;
import com.tovkaic.phonon.audio.AudioResource;
import com.tovkaic.phonon.network.packets.SyncAudioResourcesPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Server commands for managing audio resources.
 * Simple, flat command structure - no nested nonsense.
 */
public class PhononCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("phonon")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("add")
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("url", StringArgumentType.greedyString())
                        .executes(PhononCommand::addResource)
                    )
                )
            )
            .then(Commands.literal("list")
                .executes(PhononCommand::listResources)
            )
            .then(Commands.literal("remove")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(PhononCommand::removeResource)
                )
            )
        );
    }

    private static int addResource(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        String url = StringArgumentType.getString(ctx, "url");

        // MVP: Only accept .ogg direct URLs
        if (!url.toLowerCase().endsWith(".ogg")) {
            ctx.getSource().sendFailure(Component.literal(
                "MVP only supports direct .ogg URLs. Example: https://example.com/music.ogg"
            ));
            return 0;
        }

        // Validate URL format
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            ctx.getSource().sendFailure(Component.literal(
                "URL must start with http:// or https://"
            ));
            return 0;
        }

        AudioManager manager = AudioManager.getInstance();
        if (manager.getResourceByName(name).isPresent()) {
            ctx.getSource().sendFailure(Component.literal("Resource '" + name + "' already exists"));
            return 0;
        }

        AudioResource resource = new AudioResource(name, url);
        manager.addResource(resource);

        // Broadcast updated resource list to all online players
        broadcastResourceList(ctx.getSource().getServer());

        ctx.getSource().sendSuccess(
            () -> Component.literal("Added audio resource: " + name),
            true
        );
        return 1;
    }

    private static int listResources(CommandContext<CommandSourceStack> ctx) {
        AudioManager manager = AudioManager.getInstance();
        List<AudioResource> resources = manager.getAllResources();

        if (resources.isEmpty()) {
            ctx.getSource().sendSuccess(
                () -> Component.literal("No audio resources registered"),
                false
            );
            return 0;
        }

        ctx.getSource().sendSuccess(
            () -> Component.literal("Audio resources (" + resources.size() + "):"),
            false
        );
        for (AudioResource resource : resources) {
            ctx.getSource().sendSuccess(
                () -> Component.literal("  - " + resource.name() + ": " + resource.url()),
                false
            );
        }
        return resources.size();
    }

    private static int removeResource(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        AudioManager manager = AudioManager.getInstance();

        return manager.getResourceByName(name)
            .map(resource -> {
                manager.removeResource(resource.id());

                // Broadcast updated resource list to all online players
                broadcastResourceList(ctx.getSource().getServer());

                ctx.getSource().sendSuccess(
                    () -> Component.literal("Removed audio resource: " + name),
                    true
                );
                return 1;
            })
            .orElseGet(() -> {
                ctx.getSource().sendFailure(Component.literal("Resource '" + name + "' not found"));
                return 0;
            });
    }

    /**
     * Broadcast current resource list to all online players.
     * Called after add/remove operations.
     */
    private static void broadcastResourceList(MinecraftServer server) {
        var packet = new SyncAudioResourcesPacket(AudioManager.getInstance().getAllResources());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, packet);
        }
    }
}
