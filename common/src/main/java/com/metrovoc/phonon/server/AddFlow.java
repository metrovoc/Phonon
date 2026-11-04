package com.metrovoc.phonon.server;

import com.metrovoc.phonon.audio.AudioManager;
import com.metrovoc.phonon.audio.AudioResource;
import com.metrovoc.phonon.config.PhononServerConfig;
import com.metrovoc.phonon.platform.PlatformHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Adding audio, whether it came from the command or the speaker screen. */
public final class AddFlow {
    private static final List<Job> jobs = new ArrayList<>();
    /** An add that failed for lack of tools, retried once they are installed. */
    private static Runnable retryAfterInstall;

    private static final class Job {
        final String label;
        int percent = -1;

        Job(String label) {
            this.label = label;
        }
    }

    private AddFlow() {}

    /** Adds from a link, or with no link, the first result of searching for the text. Returns whether it started. */
    public static boolean addFromText(CommandSourceStack source, String text) {
        AddRequest request = AddRequest.parse(text);
        if (request == null) {
            TitleSearch.addFirstResult(source, text);
            return true;
        }
        if (request.hasName() && AudioManager.getInstance().getResourceByName(request.name()).isPresent()) {
            source.sendFailure(Component.translatable("phonon.add.error.name_taken", request.name()));
            return false;
        }
        start(source, request);
        return true;
    }

    static void start(CommandSourceStack source, AddRequest request) {
        String label = request.hasName() ? request.name() : request.url();
        source.sendSystemMessage(Component.translatable("phonon.add.processing", label).withStyle(ChatFormatting.YELLOW));

        UUID resourceId = UUID.randomUUID();
        MinecraftServer server = source.getServer();
        Job job = new Job(label);
        jobs.add(job);
        publish(server);
        ServerAudioStorage.getInstance()
            .downloadAndStore(resourceId, request.url(), percent -> server.execute(() -> {
                job.percent = percent;
                publish(server);
            }))
            .thenAccept(result -> server.execute(() -> {
                jobs.remove(job);
                publish(server);
                finish(source, request, resourceId, result);
            }));
    }

    private static void publish(MinecraftServer server) {
        List<LibraryJob> view = new ArrayList<>();
        for (Job job : jobs) {
            view.add(new LibraryJob(job.label, job.percent));
        }
        PlatformHelper.INSTANCE.broadcastLibraryJobs(server, view);
    }

    public static void clear() {
        jobs.clear();
        retryAfterInstall = null;
    }

    private static void finish(CommandSourceStack source, AddRequest request, UUID id, DownloadResult result) {
        if (!result.success()) {
            source.sendFailure(Component.translatable(result.failure().translationKey()));
            if (result.failure() == AudioFailure.TOOLS_MISSING) {
                retryAfterInstall = () -> start(source, request);
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

    public static void retryAfterInstall() {
        Runnable retry = retryAfterInstall;
        retryAfterInstall = null;
        if (retry != null) {
            retry.run();
        }
    }

    public static void offerInstall(CommandSourceStack source) {
        if (!PhononServerConfig.getAllowToolInstall()) {
            source.sendSystemMessage(Component.translatable("phonon.tools.install_disabled").withStyle(ChatFormatting.GRAY));
        } else if (!AudioTools.canInstall()) {
            source.sendSystemMessage(Component.translatable("phonon.tools.unsupported").withStyle(ChatFormatting.GRAY));
        } else {
            source.sendSystemMessage(Component.translatable("phonon.tools.offer",
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

    public static String formatDuration(long ms) {
        if (ms <= 0) return "??:??";
        long totalSeconds = ms / 1000;
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    public static String formatSize(long bytes) {
        if (bytes <= 0) return "? KB";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
