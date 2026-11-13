package com.metrovoc.phonon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.metrovoc.phonon.audio.AudioManager;
import com.metrovoc.phonon.audio.AudioPersistence;
import com.metrovoc.phonon.audio.AudioResource;
import com.metrovoc.phonon.config.PhononServerConfig;
import com.metrovoc.phonon.network.packets.SyncSettingsPacket;
import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.platform.PlatformHelper;
import com.metrovoc.phonon.server.AddFlow;
import com.metrovoc.phonon.server.ToolsFlow;
import com.metrovoc.phonon.server.AudioFailure;
import com.metrovoc.phonon.server.AudioLibrary;
import com.metrovoc.phonon.server.AudioTools;
import com.metrovoc.phonon.server.ServerAudioStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
//? if >=1.21.11
import net.minecraft.server.permissions.Permissions;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Server commands for managing audio resources.
 * Simple, flat command structure.
 */
public class PhononCommand {
    private static final AtomicBoolean installing = new AtomicBoolean();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("phonon")
            //? if >=1.21.11 {
            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
            //?} else
            /*.requires(source -> source.hasPermission(2))*/
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
            .then(configCommand())
            .then(Commands.literal("tools")
                .executes(PhononCommand::showTools)
                .then(Commands.literal("help")
                    .executes(PhononCommand::toolsHelp)
                )
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
        return AddFlow.addFromText(ctx.getSource(), StringArgumentType.getString(ctx, "name and link")) ? 1 : 0;
    }

    private static void tell(CommandSourceStack source, Component message) {
        source.sendSystemMessage(message);
    }

    private static int showTools(CommandContext<CommandSourceStack> ctx) {
        ToolsFlow.describe(ctx.getSource());
        return 1;
    }

    private static int toolsHelp(CommandContext<CommandSourceStack> ctx) {
        ToolsFlow.help(ctx.getSource());
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
        AddFlow.retryAfterInstall();
    }

    private static int updateTools(CommandContext<CommandSourceStack> ctx) {
        ToolsFlow.update(ctx.getSource(), success -> {});
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
            PlatformHelper.INSTANCE.reloadServerConfig();

            MinecraftServer server = source.getServer();
            PlatformHelper.INSTANCE.sendToAllPlayers(server, SyncSettingsPacket.current());
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

    private static LiteralArgumentBuilder<CommandSourceStack> configCommand() {
        LiteralArgumentBuilder<CommandSourceStack> config = Commands.literal("config").executes(PhononCommand::listSettings);
        for (PhononServerConfig.Setting setting : PhononServerConfig.SETTINGS) {
            var value = switch (setting.kind()) {
                case SWITCH -> Commands.argument("value", BoolArgumentType.bool())
                    .executes(ctx -> setSetting(ctx, setting, BoolArgumentType.getBool(ctx, "value")));
                case NUMBER -> Commands.argument("value", IntegerArgumentType.integer(setting.min(), setting.max()))
                    .executes(ctx -> setSetting(ctx, setting, IntegerArgumentType.getInteger(ctx, "value")));
                case TEXT -> Commands.argument("value", StringArgumentType.greedyString())
                    .executes(ctx -> setSetting(ctx, setting, StringArgumentType.getString(ctx, "value").trim()));
            };
            config.then(Commands.literal(setting.key())
                .requires(source -> mayChange(source, setting))
                .then(Commands.literal("reset").executes(ctx -> setSetting(ctx, setting, setting.fallback())))
                .then(value));
        }
        return config;
    }

    private static boolean mayChange(CommandSourceStack source, PhononServerConfig.Setting setting) {
        //? if >=1.21.11 {
        return !setting.ownersOnly() || source.permissions().hasPermission(Permissions.COMMANDS_OWNER);
        //?} else
        /*return !setting.ownersOnly() || source.hasPermission(4);*/
    }

    private static int listSettings(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        tell(source, Component.translatable("phonon.config.header").withStyle(ChatFormatting.GOLD));
        for (PhononServerConfig.Setting setting : PhononServerConfig.SETTINGS) {
            Object value = setting.current().get();
            var line = Component.literal("  " + setting.key() + ": ").withStyle(ChatFormatting.GRAY)
                .append(shown(setting.shown()).withStyle(ChatFormatting.WHITE));
            switch (setting.kind()) {
                case SWITCH -> {
                    if (mayChange(source, setting)) {
                        line.append(" ").append(AddFlow.action("/phonon config " + setting.key() + " " + !(Boolean) value,
                            Component.translatable(Boolean.TRUE.equals(value) ? "phonon.config.turn_off" : "phonon.config.turn_on")));
                    }
                }
                case NUMBER -> line.append(Component.literal(" (" + setting.min() + "–" + setting.max() + ")").withStyle(ChatFormatting.DARK_GRAY));
                case TEXT -> {}
            }
            tell(source, line);
        }
        return PhononServerConfig.SETTINGS.size();
    }

    private static net.minecraft.network.chat.MutableComponent shown(String value) {
        return value.isEmpty() ? Component.translatable("phonon.config.none") : Component.literal(value);
    }

    private static int setSetting(CommandContext<CommandSourceStack> ctx, PhononServerConfig.Setting setting, Object value) {
        CommandSourceStack source = ctx.getSource();
        try {
            PhononServerConfig.set(setting, value);
        } catch (RuntimeException e) {
            Phonon.LOGGER.error("Couldn't save {}", setting.key(), e);
            source.sendFailure(Component.translatable("phonon.config.failed", setting.key(), String.valueOf(e.getMessage())));
            return 0;
        }
        PlatformHelper.INSTANCE.sendToAllPlayers(source.getServer(), SyncSettingsPacket.current());
        source.sendSuccess(() -> Component.translatable("phonon.config.set", setting.key(), shown(setting.shown()))
            .withStyle(ChatFormatting.GREEN), true);
        return 1;
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
