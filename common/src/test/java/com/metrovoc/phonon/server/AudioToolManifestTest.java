package com.metrovoc.phonon.server;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AudioToolManifestTest {
    @Test
    void everySupportedPlatformHasEveryTool() {
        String[][] platforms = {
            {"Mac OS X", "aarch64"}, {"Mac OS X", "x86_64"}, {"Windows 11", "amd64"},
            {"Linux", "amd64"}, {"Linux", "aarch64"}
        };
        for (String[] platform : platforms) {
            List<AudioToolManifest.Asset> assets = AudioToolManifest.forPlatform(platform[0], platform[1]).orElseThrow();
            assertEquals(AudioTools.TOOLS, assets.stream().map(AudioToolManifest.Asset::tool).toList());
            for (AudioToolManifest.Asset asset : assets) {
                assertTrue(asset.sha256().matches("[0-9a-f]{64}"), asset.url());
                assertTrue(asset.url().startsWith("https://github.com/"), asset.url());
            }
        }
    }

    @Test
    void unsupportedPlatformsOfferNothing() {
        assertTrue(AudioToolManifest.forPlatform("Windows 11", "aarch64").isEmpty());
        assertTrue(AudioToolManifest.forPlatform("FreeBSD", "amd64").isEmpty());
    }
}
