package com.metrovoc.phonon.config;

import com.metrovoc.phonon.audio.AudioLimits;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Server-side configuration values.
 * Platform-specific code populates these suppliers.
 */
public final class PhononServerConfig {

    private PhononServerConfig() {}

    public static final int DEFAULT_CHUNK_SIZE = 256 * 1024;
    public static final int DEFAULT_MAX_BYTES_PER_TICK = 512 * 1024;
    public static final int DEFAULT_MAX_BYTES_PER_PLAYER_PER_TICK = 256 * 1024;
    public static final int DEFAULT_DOWNLOAD_CONNECT_TIMEOUT = 30;
    public static final int DEFAULT_DOWNLOAD_READ_TIMEOUT = 300;
    public static final int DEFAULT_MAX_AUDIO_SIZE_MB = 512;
    public static final int MAX_AUDIO_SIZE_MB = AudioLimits.MAX_AUDIO_SIZE_MB;
    public static final int DEFAULT_AUDIO_QUALITY = 5;

    // Transfer settings
    private static IntSupplier chunkSize = () -> DEFAULT_CHUNK_SIZE;
    private static IntSupplier maxBytesPerTick = () -> DEFAULT_MAX_BYTES_PER_TICK;
    private static IntSupplier maxBytesPerPlayerPerTick = () -> DEFAULT_MAX_BYTES_PER_PLAYER_PER_TICK;

    // Download settings
    private static IntSupplier downloadConnectTimeoutSeconds = () -> DEFAULT_DOWNLOAD_CONNECT_TIMEOUT;
    private static IntSupplier downloadReadTimeoutSeconds = () -> DEFAULT_DOWNLOAD_READ_TIMEOUT;
    private static IntSupplier maxAudioSizeMB = () -> DEFAULT_MAX_AUDIO_SIZE_MB;
    private static IntSupplier audioQuality = () -> DEFAULT_AUDIO_QUALITY;

    // Tool settings
    private static BooleanSupplier allowToolInstall = () -> true;

    // Permission settings
    private static BooleanSupplier ownerOnlySpeakers = () -> false;
    private static BooleanSupplier anyoneCanAddTracks = () -> false;
    private static BooleanSupplier anyoneCanControlStations = () -> false;
    private static BooleanSupplier autoUpdateYtDlp = () -> false;
    private static BooleanSupplier muffleBehindWalls = () -> true;
    private static Supplier<String> downloadProxy = () -> "";
    private static Supplier<String> cookiesFile = () -> "";

    /** A setting operators can change in game with {@code /phonon config}. Numbers are whole, from min to max. */
    public record Setting(String section, String key, Kind kind, int min, int max, Object fallback, Supplier<Object> current) {
        public enum Kind { SWITCH, NUMBER, TEXT }

        static Setting toggle(String section, String key, boolean fallback, BooleanSupplier current) {
            return new Setting(section, key, Kind.SWITCH, 0, 1, fallback, current::getAsBoolean);
        }

        static Setting number(String section, String key, int fallback, int min, int max, IntSupplier current) {
            return new Setting(section, key, Kind.NUMBER, min, max, fallback, current::getAsInt);
        }

        static Setting text(String section, String key, Supplier<String> current) {
            return new Setting(section, key, Kind.TEXT, 0, 0, "", current::get);
        }

        /** The current value as chat shows it, with any password in the proxy address hidden. */
        public String shown() {
            String value = current.get().toString();
            return key.equals("downloadProxy") ? hidePassword(value) : value;
        }

        static String hidePassword(String proxy) {
            return proxy.replaceFirst("^([A-Za-z][\\w+.-]*://)?[^/@]*@", "$1***@");
        }

        /**
         * The tools settings pick which files yt-dlp reads and writes and whether it updates itself, so only the
         * server's owners may change them.
         */
        public boolean ownersOnly() {
            return section.equals("tools");
        }
    }

    public static final List<Setting> SETTINGS = List.of(
        Setting.toggle("permissions", "ownerOnlySpeakers", false, PhononServerConfig::getOwnerOnlySpeakers),
        Setting.toggle("permissions", "anyoneCanAddTracks", false, PhononServerConfig::getAnyoneCanAddTracks),
        Setting.toggle("permissions", "anyoneCanControlStations", false, PhononServerConfig::getAnyoneCanControlStations),
        Setting.toggle("sound", "muffleBehindWalls", true, PhononServerConfig::getMuffleBehindWalls),
        Setting.number("download", "audioQuality", DEFAULT_AUDIO_QUALITY, 0, 10, PhononServerConfig::getAudioQuality),
        Setting.number("download", "maxAudioSizeMB", DEFAULT_MAX_AUDIO_SIZE_MB, 16, MAX_AUDIO_SIZE_MB, PhononServerConfig::getMaxAudioSizeMB),
        Setting.number("download", "connectTimeoutSeconds", DEFAULT_DOWNLOAD_CONNECT_TIMEOUT, 5, 120, PhononServerConfig::getDownloadConnectTimeoutSeconds),
        Setting.number("download", "readTimeoutSeconds", DEFAULT_DOWNLOAD_READ_TIMEOUT, 30, 600, PhononServerConfig::getDownloadReadTimeoutSeconds),
        Setting.toggle("tools", "allowToolInstall", true, PhononServerConfig::getAllowToolInstall),
        Setting.toggle("tools", "autoUpdateYtDlp", false, PhononServerConfig::getAutoUpdateYtDlp),
        Setting.text("tools", "downloadProxy", PhononServerConfig::getDownloadProxy),
        Setting.text("tools", "cookiesFile", PhononServerConfig::getCookiesFile),
        Setting.number("transfer", "chunkSize", DEFAULT_CHUNK_SIZE, 64 * 1024, 512 * 1024, PhononServerConfig::getChunkSize),
        Setting.number("transfer", "maxBytesPerTick", DEFAULT_MAX_BYTES_PER_TICK, 64 * 1024, 4 * 1024 * 1024, PhononServerConfig::getMaxBytesPerTick),
        Setting.number("transfer", "maxBytesPerPlayerPerTick", DEFAULT_MAX_BYTES_PER_PLAYER_PER_TICK, 64 * 1024, 2 * 1024 * 1024,
            PhononServerConfig::getMaxBytesPerPlayerPerTick));

    private static ServerConfigStore store = (section, key, value) -> {};

    /** How the loader saves a setting changed in game. */
    public static void setStore(ServerConfigStore configStore) { store = configStore; }

    /** Saves a setting to the config file and applies it. */
    public static void set(Setting setting, Object value) {
        store.set(setting.section(), setting.key(), value);
    }

    // Getters
    public static int getChunkSize() { return chunkSize.getAsInt(); }
    public static int getMaxBytesPerTick() { return maxBytesPerTick.getAsInt(); }
    public static int getMaxBytesPerPlayerPerTick() { return maxBytesPerPlayerPerTick.getAsInt(); }
    public static int getDownloadConnectTimeoutSeconds() { return downloadConnectTimeoutSeconds.getAsInt(); }
    public static int getDownloadReadTimeoutSeconds() { return downloadReadTimeoutSeconds.getAsInt(); }
    public static int getMaxAudioSizeMB() { return maxAudioSizeMB.getAsInt(); }
    public static int getAudioQuality() { return audioQuality.getAsInt(); }
    public static boolean getAllowToolInstall() { return allowToolInstall.getAsBoolean(); }
    public static boolean getOwnerOnlySpeakers() { return ownerOnlySpeakers.getAsBoolean(); }
    public static boolean getAnyoneCanAddTracks() { return anyoneCanAddTracks.getAsBoolean(); }
    public static boolean getAnyoneCanControlStations() { return anyoneCanControlStations.getAsBoolean(); }
    public static boolean getAutoUpdateYtDlp() { return autoUpdateYtDlp.getAsBoolean(); }
    public static boolean getMuffleBehindWalls() { return muffleBehindWalls.getAsBoolean(); }
    public static String getDownloadProxy() { return downloadProxy.get(); }
    public static String getCookiesFile() { return cookiesFile.get(); }

    // Setters (called by platform-specific config registration)
    public static void setChunkSize(IntSupplier supplier) { chunkSize = supplier; }
    public static void setMaxBytesPerTick(IntSupplier supplier) { maxBytesPerTick = supplier; }
    public static void setMaxBytesPerPlayerPerTick(IntSupplier supplier) { maxBytesPerPlayerPerTick = supplier; }
    public static void setDownloadConnectTimeoutSeconds(IntSupplier supplier) { downloadConnectTimeoutSeconds = supplier; }
    public static void setDownloadReadTimeoutSeconds(IntSupplier supplier) { downloadReadTimeoutSeconds = supplier; }
    public static void setMaxAudioSizeMB(IntSupplier supplier) { maxAudioSizeMB = supplier; }
    public static void setAudioQuality(IntSupplier supplier) { audioQuality = supplier; }
    public static void setAllowToolInstall(BooleanSupplier supplier) { allowToolInstall = supplier; }
    public static void setOwnerOnlySpeakers(BooleanSupplier supplier) { ownerOnlySpeakers = supplier; }
    public static void setAnyoneCanAddTracks(BooleanSupplier supplier) { anyoneCanAddTracks = supplier; }
    public static void setAnyoneCanControlStations(BooleanSupplier supplier) { anyoneCanControlStations = supplier; }
    public static void setAutoUpdateYtDlp(BooleanSupplier supplier) { autoUpdateYtDlp = supplier; }
    public static void setMuffleBehindWalls(BooleanSupplier supplier) { muffleBehindWalls = supplier; }
    public static void setDownloadProxy(Supplier<String> supplier) { downloadProxy = supplier; }
    public static void setCookiesFile(Supplier<String> supplier) { cookiesFile = supplier; }
}
