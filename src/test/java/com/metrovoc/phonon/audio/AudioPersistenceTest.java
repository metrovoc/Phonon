package com.metrovoc.phonon.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AudioPersistenceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void skipsMalformedEntriesWithoutDiscardingValidResources() throws Exception {
        UUID id = UUID.randomUUID();
        Path file = temporaryDirectory.resolve("phonon_audio.json");
        Files.writeString(file, """
            [
              {"id":"not-a-uuid","name":"Bad","url":"bad","duration":1},
              {"id":"%s","name":"Good","url":"https://example.test/good.ogg","duration":1234}
            ]
            """.formatted(id));

        List<AudioResource> resources = AudioPersistence.load(file);

        assertEquals(1, resources.size());
        assertEquals(id, resources.getFirst().id());
        assertEquals(0, resources.getFirst().sizeBytes());
    }

    @Test
    void savesThroughATemporaryFileAndRoundTripsAllMetadata() throws Exception {
        Path file = temporaryDirectory.resolve("data/phonon_audio.json");
        AudioResource resource = new AudioResource(
            UUID.randomUUID(),
            "Track",
            "https://example.test/track.ogg",
            42_000,
            8192
        );

        AudioPersistence.save(file, List.of(resource));

        assertEquals(List.of(resource), AudioPersistence.load(file));
        assertFalse(Files.exists(file.resolveSibling("phonon_audio.json.tmp")));
    }
}
