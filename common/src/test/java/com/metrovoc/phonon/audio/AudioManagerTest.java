package com.metrovoc.phonon.audio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioManagerTest {
    private final AudioManager manager = AudioManager.getInstance();

    @AfterEach
    void clearManager() {
        manager.loadResources(List.of());
    }

    @Test
    void keepsNameAndIdIndexesConsistentAcrossRename() {
        UUID id = UUID.randomUUID();
        assertTrue(manager.addResource(new AudioResource(id, "Alpha", "https://example.test/a.ogg", 1, 2)));
        manager.updateResource(new AudioResource(id, "Beta", "https://example.test/a.ogg", 3, 4));

        assertTrue(manager.getResourceByName("alpha").isEmpty());
        assertEquals(id, manager.getResourceByName("BETA").orElseThrow().id());
        assertEquals("Beta", manager.getAllResources().getFirst().name());
        assertThrows(UnsupportedOperationException.class,
            () -> manager.getAllResources().add(new AudioResource("extra", "url")));
    }

    @Test
    void rejectsCaseInsensitiveDuplicatesUsingRootLocale() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            assertTrue(manager.addResource(new AudioResource("ISTANBUL", "first")));
            assertFalse(manager.addResource(new AudioResource("istanbul", "second")));
        } finally {
            Locale.setDefault(previous);
        }
    }
}
