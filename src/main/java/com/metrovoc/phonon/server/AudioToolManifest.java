package com.metrovoc.phonon.server;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Pinned audio tool downloads per platform, verified against these checksums before use. */
final class AudioToolManifest {
    enum Packaging { RAW, GZIP, ZIP }

    record Asset(String tool, String url, String sha256, long bytes, Packaging packaging) {}

    private static final String YT_DLP = "https://github.com/yt-dlp/yt-dlp/releases/download/2026.10.26/";
    private static final String FFMPEG = "https://github.com/eugeneware/ffmpeg-static/releases/download/b6.1.1/";
    private static final String DENO = "https://github.com/denoland/deno/releases/download/v2.9.8/";

    private static final List<Asset> MAC_ARM = List.of(
        new Asset("yt-dlp", YT_DLP + "yt-dlp_macos",
            "0f922c15ccc0b83eefa1c8bc9104e434318c4bf16e408e1ee93b7745a6efe0aa", 37_466_304, Packaging.RAW),
        new Asset("ffmpeg", FFMPEG + "ffmpeg-darwin-arm64.gz",
            "8923876afa8db5585022d7860ec7e589af192f441c56793971276d450ed3bbfa", 19_246_198, Packaging.GZIP),
        new Asset("ffprobe", FFMPEG + "ffprobe-darwin-arm64.gz",
            "d986a8ec7b030899fe66a8a288ed809a3543338705a3ce178cfb85869c5d80be", 19_207_077, Packaging.GZIP),
        new Asset("deno", DENO + "deno-aarch64-apple-darwin.zip",
            "9f0bf660393da2eaff044cdc7444cb08c73f235849b61d224d825a194ce7c98e", 38_770_795, Packaging.ZIP));

    private static final List<Asset> MAC_X64 = List.of(
        new Asset("yt-dlp", YT_DLP + "yt-dlp_macos",
            "0f922c15ccc0b83eefa1c8bc9104e434318c4bf16e408e1ee93b7745a6efe0aa", 37_466_304, Packaging.RAW),
        new Asset("ffmpeg", FFMPEG + "ffmpeg-darwin-x64.gz",
            "929b375c1182d956c51f7ac25e0b2b0411fb01f6f407aa15c9758efeb4242106", 25_296_431, Packaging.GZIP),
        new Asset("ffprobe", FFMPEG + "ffprobe-darwin-x64.gz",
            "d4da574d6e2e197bd259b47d69cf262df9e312af24ad960444f6d806d3d4c186", 25_239_438, Packaging.GZIP),
        new Asset("deno", DENO + "deno-x86_64-apple-darwin.zip",
            "4b2f5edd2639b7470f034238b321d7002d8dfb4726d8558f52c17afac370eb2c", 42_797_031, Packaging.ZIP));

    private static final List<Asset> WINDOWS_X64 = List.of(
        new Asset("yt-dlp", YT_DLP + "yt-dlp.exe",
            "f5389d3698ecde4ef06f4e6b1ce0294fe9ac1396228c8990b8d3416d6d177a7d", 23_575_744, Packaging.RAW),
        new Asset("ffmpeg", FFMPEG + "ffmpeg-win32-x64.gz",
            "8883a3dffbd0a16cf4ef95206ea05283f78908dbfb118f73c83f4951dcc06d77", 29_581_307, Packaging.GZIP),
        new Asset("ffprobe", FFMPEG + "ffprobe-win32-x64.gz",
            "f309e6223ad89d2fe54bccd420a7709b66fd27540674e92309578ed491a43c8d", 29_521_644, Packaging.GZIP),
        new Asset("deno", DENO + "deno-x86_64-pc-windows-msvc.zip",
            "cba84b26e0df5ae7f74fc66b86a95b866872a86292ebd84fa1e959927edd93bf", 42_917_472, Packaging.ZIP));

    private static final List<Asset> LINUX_X64 = List.of(
        new Asset("yt-dlp", YT_DLP + "yt-dlp_linux",
            "767ba9dce7052aa196f8b12652f433d76de128ba168966fa84b6394d5e6c6795", 41_166_968, Packaging.RAW),
        new Asset("ffmpeg", FFMPEG + "ffmpeg-linux-x64.gz",
            "bfe8a8fc511530457b528c48d77b5737527b504a3797a9bc4866aeca69c2dffa", 29_354_986, Packaging.GZIP),
        new Asset("ffprobe", FFMPEG + "ffprobe-linux-x64.gz",
            "25d9b6ccb05e3d9de9e04e31e2506d8dd7f9f0418981965ac6df12e8d3afd067", 29_276_839, Packaging.GZIP),
        new Asset("deno", DENO + "deno-x86_64-unknown-linux-gnu.zip",
            "e890975bc2549807bbcc0b584ffafda72cc7c93349dd99c80483dd784f6c4609", 41_963_064, Packaging.ZIP));

    private static final List<Asset> LINUX_ARM = List.of(
        new Asset("yt-dlp", YT_DLP + "yt-dlp_linux_aarch64",
            "e87e9533e89fe5889abaa21f5114dfe7756d6aa6219b83551add04d48b18e483", 40_789_792, Packaging.RAW),
        new Asset("ffmpeg", FFMPEG + "ffmpeg-linux-arm64.gz",
            "754a678672298bc68156adff58aa7385a592c2b30b1d0ae8750c45c915c4bac0", 25_568_691, Packaging.GZIP),
        new Asset("ffprobe", FFMPEG + "ffprobe-linux-arm64.gz",
            "2ab6aba60ee84412dff9188720703376cb4e7aaf7e0b5e43aa8249f2acae5bf8", 25_493_573, Packaging.GZIP),
        new Asset("deno", DENO + "deno-aarch64-unknown-linux-gnu.zip",
            "d49596c20b9788cf8f5d8ddf644103d4bde56ed239753f0bdb92ba9361d51905", 40_120_596, Packaging.ZIP));

    private AudioToolManifest() {}

    static Optional<List<Asset>> forPlatform(String osName, String osArch) {
        String os = osName.toLowerCase(Locale.ROOT);
        boolean arm = osArch.toLowerCase(Locale.ROOT).contains("aarch64") || osArch.equalsIgnoreCase("arm64");
        boolean x64 = osArch.equalsIgnoreCase("amd64") || osArch.equalsIgnoreCase("x86_64");
        if (os.contains("mac") || os.contains("darwin")) {
            return arm ? Optional.of(MAC_ARM) : x64 ? Optional.of(MAC_X64) : Optional.empty();
        }
        if (os.contains("win")) {
            return x64 ? Optional.of(WINDOWS_X64) : Optional.empty();
        }
        if (os.contains("linux")) {
            return arm ? Optional.of(LINUX_ARM) : x64 ? Optional.of(LINUX_X64) : Optional.empty();
        }
        return Optional.empty();
    }
}
