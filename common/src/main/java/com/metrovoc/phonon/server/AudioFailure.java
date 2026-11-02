package com.metrovoc.phonon.server;

import java.util.List;
import java.util.Locale;

/** Why audio could not be added, each with a chat message that says what to do. */
public enum AudioFailure {
    TOOLS_MISSING,
    NOT_FOUND,
    RESTRICTED,
    BOT_CHECK,
    REGION,
    TOO_LARGE,
    UNSUPPORTED,
    NETWORK,
    TIMEOUT,
    INVALID_AUDIO,
    UNKNOWN;

    private record Rule(AudioFailure failure, List<String> phrases) {}

    /** Checked in order: the first matching phrase names the cause. */
    private static final List<Rule> RULES = List.of(
        new Rule(TOO_LARGE, List.of("larger than max-filesize")),
        new Rule(BOT_CHECK, List.of("not a bot")),
        new Rule(REGION, List.of("not available in your country", "not available from your location",
            "blocked in your country", "geo restrict", "geo-restrict")),
        new Rule(RESTRICTED, List.of("private video", "sign in", "login required", "members-only",
            "confirm your age", "http error 401", "http error 403")),
        new Rule(NOT_FOUND, List.of("http error 404", "http error 410", "video unavailable",
            "has been removed", "does not exist")),
        new Rule(UNSUPPORTED, List.of("unsupported url", "is not a valid url", "no video could be found",
            "no video formats found")),
        new Rule(NETWORK, List.of("timed out", "getaddrinfo", "name resolution", "nodename nor servname",
            "network is unreachable", "connection refused", "connection reset", "ssl:")),
        new Rule(TOOLS_MISSING, List.of("ffmpeg not found", "ffprobe and ffmpeg not found", "ffprobe not found",
            "no supported javascript runtime"))
    );

    /** Reads the cause from yt-dlp's log. */
    public static AudioFailure classify(String log) {
        String lower = log.toLowerCase(Locale.ROOT);
        for (Rule rule : RULES) {
            if (rule.phrases().stream().anyMatch(lower::contains)) {
                return rule.failure();
            }
        }
        return UNKNOWN;
    }

    public static AudioFailure fromHttpStatus(int status) {
        return switch (status) {
            case 401, 403 -> RESTRICTED;
            case 404, 410 -> NOT_FOUND;
            default -> UNKNOWN;
        };
    }

    public String translationKey() {
        return "phonon.add.error." + name().toLowerCase(Locale.ROOT);
    }
}
