package com.metrovoc.phonon.server;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.config.PhononServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Keeps yt-dlp working: says how old it is and what it last managed, and updates it when asked. */
public final class ToolsFlow {
    private static final long STALE_DAYS = 30;
    private static final AtomicBoolean updating = new AtomicBoolean();
    private static final Map<String, Instant> lastWorked = new ConcurrentHashMap<>();
    private static final Map<String, Instant> lastBlocked = new ConcurrentHashMap<>();

    private ToolsFlow() {}

    public static void worked(String site) {
        lastWorked.put(site, Instant.now());
    }

    public static void failed(String site, AudioFailure failure) {
        if (failure == AudioFailure.BOT_CHECK || failure == AudioFailure.REFUSED || failure == AudioFailure.RATE_LIMITED) {
            lastBlocked.put(site, Instant.now());
        }
    }

    /** yt-dlp's site name for a link, for reporting. */
    static String siteOf(String url) {
        String host = url.replaceFirst("^https?://(www\\.)?", "");
        int slash = host.indexOf('/');
        return slash < 0 ? host : host.substring(0, slash);
    }

    private static long ytDlpAge() {
        return AudioTools.ageInDays(AudioTools.version("yt-dlp"), LocalDate.now());
    }

    /** {@code /phonon tools}: versions, age, and what last worked. */
    public static void describe(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ServerAudioStorage.getInstance().runAsync(() -> {
            boolean missing = false;
            for (AudioTools.Status status : AudioTools.status()) {
                String version = status.source() == AudioTools.Source.MISSING ? null : AudioTools.version(status.tool());
                missing |= version == null;
                boolean ytDlp = status.tool().equals("yt-dlp");
                long age = ytDlp ? AudioTools.ageInDays(version, LocalDate.now()) : -1;
                server.execute(() -> source.sendSystemMessage(line(status, version, age)));
            }
            boolean anyMissing = missing;
            server.execute(() -> {
                lastWorked.forEach((site, when) -> source.sendSystemMessage(
                    Component.translatable("phonon.tools.last_worked", site, ago(when)).withStyle(ChatFormatting.GRAY)));
                lastBlocked.forEach((site, when) -> source.sendSystemMessage(
                    Component.translatable("phonon.tools.last_blocked", site, ago(when)).withStyle(ChatFormatting.YELLOW)
                        .append(" ").append(AddFlow.action("/phonon tools help", Component.translatable("phonon.tools.what_can_i_do")))));
                if (anyMissing) {
                    AddFlow.offerInstall(source);
                }
            });
        });
    }

    private static Component line(AudioTools.Status status, String version, long age) {
        if (version == null) {
            return Component.translatable("phonon.tools.status.missing", status.tool());
        }
        String key = status.source() == AudioTools.Source.INSTALLED ? "phonon.tools.status.installed" : "phonon.tools.status.system";
        var text = Component.translatable(key, status.tool() + " " + version);
        if (age >= STALE_DAYS) {
            text.append(" ").append(Component.translatable("phonon.tools.days_old", age).withStyle(ChatFormatting.YELLOW));
            text.append(" ").append(AddFlow.action("/phonon tools update", Component.translatable("phonon.tools.update_button")));
        }
        return text;
    }

    private static String ago(Instant when) {
        Duration since = Duration.between(when, Instant.now());
        if (since.toHours() >= 1) {
            return since.toHours() + " h";
        }
        return Math.max(1, since.toMinutes()) + " min";
    }

    /** {@code /phonon tools help}: what a server owner can do about being blocked. */
    public static void help(CommandSourceStack source) {
        for (String key : new String[] {"intro", "proxy", "cookies", "token", "update"}) {
            source.sendSystemMessage(Component.translatable("phonon.tools.help." + key).withStyle(ChatFormatting.GRAY));
        }
    }

    /** Tells an operator once, as they join, that yt-dlp has got old. */
    public static void noticeIfStale(ServerPlayer player) {
        if (!PlayerPermissions.isOperator(player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        ServerAudioStorage.getInstance().runAsync(() -> {
            long age = ytDlpAge();
            if (age >= STALE_DAYS) {
                server.execute(() -> player.sendSystemMessage(Component.translatable("phonon.tools.stale", age).withStyle(ChatFormatting.YELLOW)
                    .append(" ").append(AddFlow.action("/phonon tools update", Component.translatable("phonon.tools.update_button")))));
            }
        });
    }

    /** At server start, only for owners who turned this on. */
    public static void autoUpdate() {
        if (!PhononServerConfig.getAutoUpdateYtDlp() || AudioTools.installed("yt-dlp").isEmpty()) {
            return;
        }
        ServerAudioStorage.getInstance().runAsync(() -> {
            if (ytDlpAge() >= STALE_DAYS) {
                try {
                    Phonon.LOGGER.info("autoUpdateYtDlp: {}", AudioTools.updateYtDlp() ? "updated yt-dlp" : "couldn't update yt-dlp");
                } catch (Exception e) {
                    Phonon.LOGGER.warn("autoUpdateYtDlp failed", e);
                }
            }
        });
    }

    /** Updates yt-dlp, or installs Phonon's own if it's only on the system, then reports success on the server thread. */
    public static void update(CommandSourceStack source, java.util.function.Consumer<Boolean> done) {
        MinecraftServer server = source.getServer();
        if (AudioTools.installed("yt-dlp").isEmpty() && !PhononServerConfig.getAllowToolInstall()) {
            source.sendFailure(Component.translatable("phonon.tools.update_system"));
            done.accept(false);
            return;
        }
        if (!updating.compareAndSet(false, true)) {
            source.sendFailure(Component.translatable("phonon.tools.busy"));
            done.accept(false);
            return;
        }
        source.sendSystemMessage(Component.translatable("phonon.tools.updating").withStyle(ChatFormatting.YELLOW));
        ServerAudioStorage.getInstance().runAsync(() -> {
            boolean updated;
            try {
                updated = AudioTools.updateYtDlp();
            } catch (Exception e) {
                Phonon.LOGGER.error("Failed to update yt-dlp", e);
                updated = false;
            }
            updating.set(false);
            boolean success = updated;
            server.execute(() -> {
                if (success) {
                    source.sendSystemMessage(Component.translatable("phonon.tools.updated").withStyle(ChatFormatting.GREEN));
                } else {
                    source.sendFailure(Component.translatable("phonon.tools.update_failed"));
                }
                done.accept(success);
            });
        });
    }
}
