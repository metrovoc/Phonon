package com.metrovoc.phonon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.metrovoc.phonon.audio.AudioManager;
import com.metrovoc.phonon.audio.AudioPersistence;
import com.metrovoc.phonon.audio.AudioResource;
import com.metrovoc.phonon.config.NeoForgeServerConfig;
import com.metrovoc.phonon.network.packets.SyncAudioResourcesPacket;
import com.metrovoc.phonon.server.FFmpegHelper;
import com.metrovoc.phonon.server.ServerAudioStorage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Server commands for managing audio resources.
 * Simple, flat command structure.
 */
public class PhononCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("phonon")
            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
            .then(Commands.literal("add")
                .then(Commands.argument("name", StringArgumentType.string())
                    .then(Commands.argument("url", StringArgumentType.greedyString())
                        .executes(PhononCommand::addResource)
                    )
                )
            )
            .then(Commands.literal("list")
                .executes(PhononCommand::listResources)
            )
            .then(Commands.literal("remove")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(PhononCommand::suggestResourceNames)
                    .executes(PhononCommand::removeResource)
                )
            )
            .then(Commands.literal("reload")
                .executes(PhononCommand::reloadConfig)
            )
        );
    }

    /**
     * Tab completion: list all resource names
     */
    private static CompletableFuture<Suggestions> suggestResourceNames(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
            AudioManager.getInstance().getAllResources().stream().map(AudioResource::name),
            builder
        );
    }

    private static int addResource(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        String url = StringArgumentType.getString(ctx, "url");

        // Check tool availability
        boolean hasYtDlp = FFmpegHelper.isYtDlpAvailable();
        String normalizedUrl = url.toLowerCase(Locale.ROOT);
        boolean isDirectOgg = normalizedUrl.endsWith(".ogg") || normalizedUrl.contains(".ogg?");

        if (!hasYtDlp && !isDirectOgg) {
            ctx.getSource().sendFailure(Component.literal(
                "yt-dlp not found. Only direct .ogg URLs are supported.\n" +
                "Install yt-dlp: https://github.com/yt-dlp/yt-dlp"
            ));
            return 0;
        }

        AudioManager manager = AudioManager.getInstance();
        if (manager.getResourceByName(name).isPresent()) {
            ctx.getSource().sendFailure(Component.literal("Resource '" + name + "' already exists"));
            return 0;
        }

        UUID resourceId = UUID.randomUUID();

        ctx.getSource().sendSuccess(
            () -> Component.literal("\u00A7eProcessing '\u00A7f" + name + "\u00A7e'... (downloading & converting)"),
            false
        );

        ServerAudioStorage storage = ServerAudioStorage.getInstance();
        storage.downloadAndStore(resourceId, url)
            .thenAccept(success -> {
                MinecraftServer server = ctx.getSource().getServer();
                server.execute(() -> {
                    if (success) {
                        long durationMs = storage.getDurationMs(resourceId);
                        long sizeBytes = storage.getAudioSize(resourceId);

                        AudioResource resource = new AudioResource(resourceId, name, url, durationMs, sizeBytes);
                        if (!manager.addResource(resource)) {
                            storage.deleteAudio(resourceId);
                            ctx.getSource().sendFailure(Component.literal(
                                "\u00A7cResource '" + name + "' was added by another request"
                            ));
                            return;
                        }

                        broadcastResourceList(server);

                        String info = formatDuration(durationMs) + ", " + formatSize(sizeBytes);
                        ctx.getSource().sendSuccess(
                            () -> Component.literal("\u00A7aAdded: \u00A7f" + name + " \u00A77(" + info + ")"),
                            true
                        );
                    } else {
                        ctx.getSource().sendFailure(Component.literal(
                            "\u00A7cFailed to download/convert audio. Check server logs."
                        ));
                    }
                });
            });

        return 1;
    }

    private static int listResources(CommandContext<CommandSourceStack> ctx) {
        AudioManager manager = AudioManager.getInstance();
        List<AudioResource> resources = manager.getAllResources();

        if (resources.isEmpty()) {
            ctx.getSource().sendSuccess(
                () -> Component.literal("\u00A77No audio resources registered."),
                false
            );
            return 0;
        }

        ctx.getSource().sendSuccess(
            () -> Component.literal("\u00A76Audio Resources \u00A77(" + resources.size() + "):"),
            false
        );

        ServerAudioStorage storage = ServerAudioStorage.getInstance();
        for (AudioResource resource : resources) {
            boolean cached = storage.hasAudio(resource.id());
            String status = cached ? "\u00A7a\u2713" : "\u00A7c\u2717";
            String duration = formatDuration(resource.durationMs());
            String size = formatSize(resource.sizeBytes());

            ctx.getSource().sendSuccess(
                () -> Component.literal("  " + status + " \u00A7f" + resource.name() + " \u00A77[" + duration + "] \u00A78(" + size + ")"),
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
                ServerAudioStorage.getInstance().deleteAudio(resource.id());
                broadcastResourceList(ctx.getSource().getServer());

                ctx.getSource().sendSuccess(
                    () -> Component.literal("\u00A7aRemoved: \u00A7f" + name),
                    true
                );
                return 1;
            })
            .orElseGet(() -> {
                ctx.getSource().sendFailure(Component.literal("\u00A7cResource '" + name + "' not found"));
                return 0;
            });
    }

    /**
     * Broadcast current resource list to all online players.
     */
    private static void broadcastResourceList(MinecraftServer server) {
        var packet = new SyncAudioResourcesPacket(AudioManager.getInstance().getAllResources());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, packet);
        }
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        try {
            NeoForgeServerConfig.SPEC.afterReload();
            NeoForgeServerConfig.bind();

            MinecraftServer server = ctx.getSource().getServer();
            Path worldDir = server.getWorldPath(LevelResource.ROOT);
            Path dataFile = worldDir.resolve("phonon_audio.json");

            AudioManager manager = AudioManager.getInstance();
            List<AudioResource> resources = AudioPersistence.load(dataFile);
            manager.loadResources(resources);

            broadcastResourceList(server);

            ctx.getSource().sendSuccess(
                () -> Component.literal("\u00A7aReloaded config and " + resources.size() + " audio resources"),
                true
            );
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("\u00A7cFailed to reload: " + e.getMessage()));
            return 0;
        }
    }

    private static String formatDuration(long ms) {
        if (ms <= 0) return "??:??";
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0) return "? KB";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
