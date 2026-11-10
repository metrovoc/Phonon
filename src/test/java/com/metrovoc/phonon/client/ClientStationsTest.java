package com.metrovoc.phonon.client;

import com.metrovoc.phonon.station.Station;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClientStationsTest {
    private final ClientStations client = ClientStations.getInstance();

    @AfterEach
    void clear() {
        client.clear();
    }

    @Test
    void targetedUpdateRefreshesSharedCatalogWithoutDroppingOtherStations() {
        Station first = new Station(UUID.randomUUID(), "First", null, "", true);
        Station second = new Station(UUID.randomUUID(), "Second", null, "", true);
        client.setShared(List.of(first.view(), second.view()), List.of());
        first.cycleRepeat();
        client.put(first.view());
        assertEquals(List.of(first.view(), second.view()), client.shared());
        client.put(Station.ownedBySpeaker().view());
        assertEquals(2, client.shared().size());
    }
}
