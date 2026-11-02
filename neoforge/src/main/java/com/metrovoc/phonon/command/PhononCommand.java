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
import com.metrovoc.phonon.config.PhononServerConfig;
import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.platform.PlatformHelper;
import com.metrovoc.phonon.server.AddRequest;
import com.metrovoc.phonon.server.AudioFailure;
import com.metrovoc.phonon.server.AudioLibrary;
import com.metrovoc.phonon.server.AudioTools;
import com.metrovoc.phonon.server.DownloadResult;
import com.metrovoc.phonon.server.ServerAudioStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Server commands for managing audio resources.
 * Simple, flat command structure.
 */
public class PhononCommand {
    private static final AtomicBoolean installing = new AtomicBoolean();

    /** An add that failed for lack of tools, retried once they are installed. */
    private static Runnable retryAfterInstall;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("phonon")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("add")
                .then(Commands.argument("name and link", StringArgumentType.greedyString())
                    .executes(PhononCommand::addResource)
                )
            )
            .then(Commands.literal("list")
                .executes(PhononCommand::listResources)
            )
            .then(Commands.literal("remove")
                .then(Commands.argument("name", StringArgumentType.greedyString())
                    .suggests(PhononCommand::suggestResourceNames)
                    .executes(PhononCommand::removeResource)
                )
            )
            .then(Commands.literal("reload")
                .executes(PhononCommand::reloadConfig)
            )
            .then(Commands.literal("tools")
                .executes(PhononCommand::showTools)
                .then(Commands.literal("install")
                    .executes(PhononCommand::installTools)
                )
                .then(Commands.literal("update")
                    .executes(PhononCommand::updateTools)
                )
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
        CommandSourceStack source = ctx.getSource();
        AddRequest request = AddRequest.parse(StringArgumentType.getString(ctx, "name and link"));
        if (request == null) {
            source.sendFailure(Component.translatable("phonon.add.error.bad_link"));
            return 0;
        }
        if (request.hasName() && AudioManager.getInstance().getResourceByName(request.name()).isPresent()) {
            source.sendFailure(Component.translatable("phonon.add.error.name_taken", request.name()));
            return 0;
        }
        startAdd(source, request);
        return 1;
    }

    private static void startAdd(CommandSourceStack source, AddRequest request) {
        tell(source, Component.translatable("phonon.add.processing", request.hasName() ? request.name() : request.url())
            .withStyle(ChatFormatting.YELLOW));

        UUID resourceId = UUID.randomUUID();
        MinecraftServer server = source.getServer();
        ServerAudioStorage.getInstance().downloadAndStore(resourceId, request.url())
            .thenAccept(result -> server.execute(() -> finishAdd(source, request, resourceId, result)));
    }

    private static void finishAdd(CommandSourceStack source, AddRequest request, UUID id, DownloadResult result) {
        if (!result.success()) {
            source.sendFailure(Component.translatable(result.failure().translationKey()));
            if (result.failure() == AudioFailure.TOOLS_MISSING) {
                retryAfterInstall = () -> startAdd(source, request);
                offerInstall(source);
            }
            return;
        }

        String name = AudioLibrary.uniqueName(request.hasName() ? request.name() : result.title());
        AudioResource resource = AudioLibrary.register(source.getServer(), id, name, request.url());
        if (resource == null) {
            source.sendFailure(Component.translatable("phonon.add.error.unknown"));
            return;
        }
        source.sendSuccess(() -> Component.translatable("phonon.add.done",
            Component.literal(name).withStyle(ChatFormatting.WHITE),
            formatDuration(resource.durationMs()),
            formatSize(resource.sizeBytes())
        ).withStyle(ChatFormatting.GREEN), true);
    }

    private static void offerInstall(CommandSourceStack source) {
        if (!PhononServerConfig.getAllowToolInstall()) {
            tell(source, Component.translatable("phonon.tools.install_disabled").withStyle(ChatFormatting.GRAY));
        } else if (!AudioTools.canInstall()) {
            tell(source, Component.translatable("phonon.tools.unsupported").withStyle(ChatFormatting.GRAY));
        } else {
            tell(source, Component.translatable("phonon.tools.offer",
                action("/phonon tools install", Component.translatable(
                    "phonon.tools.install_button", formatSize(AudioTools.installBytes())))));
        }
    }

    /** Bracketed, clickable chat text that runs a command, like vanilla's chat actions. */
    private static MutableComponent action(String command, Component label) {
        return ComponentUtils.wrapInSquareBrackets(label).withStyle(style -> style
            .withColor(ChatFormatting.GREEN)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }

    /** Delivered even with sendCommandFeedback off, since these are replies the player is waiting for. */
    private static void tell(CommandSourceStack source, Component message) {
        source.sendSystemMessage(message);
    }

    private static int showTools(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        boolean missing = false;
        for (AudioTools.Status status : AudioTools.status()) {
            missing |= status.source() == AudioTools.Source.MISSING;
            tell(source, Component.translatable("phonon.tools.status." + status.source().name().toLowerCase(java.util.Locale.ROOT),
                status.tool()));
        }
        if (missing) {
            offerInstall(source);
        }
        return 1;
    }

    private static int installTools(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!PhononServerConfig.getAllowToolInstall()) {
            source.sendFailure(Component.translatable("phonon.tools.install_disabled"));
            return 0;
        }
        if (!AudioTools.canInstall()) {
            source.sendFailure(Component.translatable("phonon.tools.unsupported"));
            return 0;
        }
        if (!installing.compareAndSet(false, true)) {
            source.sendFailure(Component.translatable("phonon.tools.busy"));
            return 0;
        }

        MinecraftServer server = source.getServer();
        tell(source, Component.translatable("phonon.tools.installing", formatSize(AudioTools.installBytes()))
            .withStyle(ChatFormatting.YELLOW));
        ServerAudioStorage.getInstance().runAsync(() -> {
            String error = null;
            try {
                AudioTools.install(tool -> server.execute(() -> tell(source,
                    Component.translatable("phonon.tools.installing_one", tool).withStyle(ChatFormatting.GRAY))));
            } catch (Exception e) {
                Phonon.LOGGER.error("Failed to install audio tools", e);
                error = e.getMessage();
            }
            String failure = error;
            server.execute(() -> finishInstall(source, failure));
        });
        return 1;
    }

    private static void finishInstall(CommandSourceStack source, String error) {
        installing.set(false);
        if (error != null) {
            source.sendFailure(Component.translatable("phonon.tools.install_failed", error));
            return;
        }
        tell(source, Component.translatable("phonon.tools.installed").withStyle(ChatFormatting.GREEN));
        Runnable retry = retryAfterInstall;
        retryAfterInstall = null;
        if (retry != null) {
            retry.run();
        }
    }

    private static int updateTools(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (AudioTools.installed("yt-dlp").isEmpty()) {
            source.sendFailure(Component.translatable("phonon.tools.update_system"));
            return 0;
        }
        MinecraftServer server = source.getServer();
        tell(source, Component.translatable("phonon.tools.updating").withStyle(ChatFormatting.YELLOW));
        ServerAudioStorage.getInstance().runAsync(() -> {
            boolean updated;
            try {
                updated = AudioTools.updateYtDlp();
            } catch (Exception e) {
                Phonon.LOGGER.error("Failed to update yt-dlp", e);
                updated = false;
            }
            boolean success = updated;
            server.execute(() -> {
                if (success) {
                    tell(source, Component.translatable("phonon.tools.updated").withStyle(ChatFormatting.GREEN));
                } else {
                    source.sendFailure(Component.translatable("phonon.tools.update_failed"));
                }
            });
        });
        return 1;
    }

    private static int listResources(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        List<AudioResource> resources = AudioManager.getInstance().getAllResources();

        if (resources.isEmpty()) {
            tell(source, Component.translatable("phonon.list.empty",
                AudioLibrary.musicFolder(source.getServer()).toString()).withStyle(ChatFormatting.GRAY));
            return 0;
        }

        tell(source, Component.translatable("phonon.list.header", resources.size()).withStyle(ChatFormatting.GOLD));

        ServerAudioStorage storage = ServerAudioStorage.getInstance();
        for (AudioResource resource : resources) {
            boolean cached = storage.hasAudio(resource.id());
            tell(source, Component.literal(cached ? "  ✓ " : "  ✗ ")
                .withStyle(cached ? ChatFormatting.GREEN : ChatFormatting.RED)
                .append(Component.literal(resource.name()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" [" + formatDuration(resource.durationMs()) + "]").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" (" + formatSize(resource.sizeBytes()) + ")").withStyle(ChatFormatting.DARK_GRAY)));
        }
        return resources.size();
    }

    private static int removeResource(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");

        return AudioManager.getInstance().getResourceByName(name)
            .map(resource -> {
                AudioLibrary.remove(source.getServer(), resource);
                source.sendSuccess(() -> Component.translatable("phonon.remove.done",
                    Component.literal(name).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GREEN), true);
                if (AudioLibrary.isFromMusicFolder(resource)) {
                    tell(source, Component.translatable("phonon.remove.from_folder").withStyle(ChatFormatting.GRAY));
                }
                return 1;
            })
            .orElseGet(() -> {
                source.sendFailure(Component.translatable("phonon.remove.not_found", name));
                return 0;
            });
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        try {
            NeoForgeServerConfig.SPEC.afterReload();
            NeoForgeServerConfig.bind();

            MinecraftServer server = source.getServer();
            List<AudioResource> resources = AudioPersistence.load(AudioLibrary.dataFile(server));
            AudioManager.getInstance().loadResources(resources);
            PlatformHelper.INSTANCE.broadcastAudioResources(server);

            source.sendSuccess(() -> Component.translatable("phonon.reload.done", resources.size())
                .withStyle(ChatFormatting.GREEN), true);
            int importing = AudioLibrary.importMusicFolder(server, (file, failure) -> reportImport(source, file, failure));
            if (importing > 0) {
                tell(source, Component.translatable("phonon.import.started", importing).withStyle(ChatFormatting.YELLOW));
            }
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.translatable("phonon.reload.failed", String.valueOf(e.getMessage())));
            return 0;
        }
    }

    private static void reportImport(CommandSourceStack source, String file, AudioFailure failure) {
        if (failure == null) {
            tell(source, Component.translatable("phonon.import.done", file).withStyle(ChatFormatting.GREEN));
        } else {
            source.sendFailure(Component.translatable("phonon.import.failed", file,
                Component.translatable(failure.translationKey())));
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
