package com.metrovoc.phonon.station;

import com.metrovoc.phonon.audio.PlaybackState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StationTest {
    private static final Random RANDOM = new Random(7);

    private static List<UUID> tracks(int count) {
        List<UUID> tracks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tracks.add(new UUID(0, i));
        }
        return tracks;
    }

    private static Station playing(List<UUID> tracks, int index) {
        Station station = Station.ownedBySpeaker();
        station.playFrom(tracks, index, RANDOM);
        return station;
    }

    @Test
    void endOfTrackMovesThroughQueueThenStops() {
        List<UUID> t = tracks(2);
        Station station = playing(t, 0);
        station.onTrackEnded(RANDOM);
        assertEquals(t.get(1), station.playback().resourceId());
        station.onTrackEnded(RANDOM);
        assertTrue(station.playback().isStopped());
    }

    @Test
    void repeatAllWrapsAndRepeatOneStays() {
        List<UUID> t = tracks(2);
        Station station = playing(t, 1);
        station.cycleRepeat();
        assertEquals(Station.Repeat.ALL, station.repeat());
        station.onTrackEnded(RANDOM);
        assertEquals(t.get(0), station.playback().resourceId());
        station.cycleRepeat();
        assertEquals(Station.Repeat.ONE, station.repeat());
        station.onTrackEnded(RANDOM);
        assertEquals(t.get(0), station.playback().resourceId());
        assertEquals(0, station.playback().positionAtAnchorMs());
    }

    @Test
    void nextIgnoresRepeatOneAndWraps() {
        List<UUID> t = tracks(2);
        Station station = playing(t, 1);
        station.cycleRepeat();
        station.cycleRepeat();
        station.next(RANDOM);
        assertEquals(t.get(0), station.playback().resourceId());
    }

    @Test
    void previousGoesBackAtTheStartOfATrack() {
        List<UUID> t = tracks(3);
        Station station = playing(t, 1);
        station.previous();
        assertEquals(t.get(0), station.playback().resourceId());
    }

    @Test
    void previousRestartsATrackThatIsUnderway() {
        List<UUID> t = tracks(3);
        Station station = playing(t, 1);
        station.seek(60_000);
        station.previous();
        assertEquals(t.get(1), station.playback().resourceId());
        assertEquals(0, station.playback().positionAtAnchorMs());
    }

    @Test
    void shuffleKeepsCurrentAndEveryTrack() {
        List<UUID> t = tracks(50);
        Station station = playing(t, 10);
        station.toggleShuffle(new Random(1));
        assertEquals(t.get(10), station.currentTrack());
        assertEquals(new HashSet<>(t), new HashSet<>(station.queue()));
        assertNotEquals(t, station.queue());
        assertEquals(t.subList(0, 11), station.queue().subList(0, 11));
    }

    @Test
    void playNextGoesRightAfterCurrent() {
        List<UUID> t = tracks(3);
        UUID extra = new UUID(1, 1);
        Station station = playing(t, 0);
        station.playNext(extra);
        station.onTrackEnded(RANDOM);
        assertEquals(extra, station.playback().resourceId());
    }

    @Test
    void playNextOnEmptyStationStartsIt() {
        UUID extra = new UUID(1, 1);
        Station station = Station.ownedBySpeaker();
        station.playNext(extra);
        assertEquals(extra, station.playback().resourceId());
    }

    @Test
    void removingEarlierEntryKeepsCurrentPlaying() {
        List<UUID> t = tracks(3);
        Station station = playing(t, 2);
        station.removeAt(0);
        assertEquals(t.get(2), station.currentTrack());
        assertEquals(t.get(2), station.playback().resourceId());
    }

    @Test
    void removingCurrentMovesOnOrStops() {
        List<UUID> t = tracks(2);
        Station station = playing(t, 0);
        station.removeAt(0);
        assertEquals(t.get(1), station.playback().resourceId());
        station.removeAt(0);
        assertTrue(station.playback().isStopped());
        assertTrue(station.queue().isEmpty());
    }

    @Test
    void forgettingATrackRemovesEveryMention() {
        List<UUID> t = tracks(2);
        Station station = playing(List.of(t.get(0), t.get(1), t.get(0)), 1);
        station.forget(t.get(0));
        assertEquals(List.of(t.get(1)), station.queue());
        assertEquals(t.get(1), station.playback().resourceId());
    }

    @Test
    void pauseAndResumeKeepPosition() {
        Station station = playing(tracks(1), 0);
        station.seek(5000);
        station.pause();
        assertTrue(station.playback().isPaused());
        long held = station.playback().getCurrentPositionMs();
        station.resume();
        assertTrue(station.playback().isPlaying());
        assertTrue(station.playback().getCurrentPositionMs() >= held);
    }

    @Test
    void jsonRoundTripKeepsEverything() {
        List<UUID> t = tracks(3);
        UUID owner = new UUID(5, 5);
        Station station = new Station(new UUID(9, 9), "Radio Central", owner, "Dev", true);
        station.playFrom(t, 1, RANDOM);
        station.cycleRepeat();
        station.toggleShuffle(RANDOM);
        station.seek(4000);
        Station loaded = Station.fromJson(station.toJson());
        assertEquals(station.view(), loaded.view());
        assertEquals(station.playback().resourceId(), loaded.playback().resourceId());
        assertTrue(loaded.playback().getCurrentPositionMs() >= 4000);
    }

    @Test
    void migratedSpeakerKeepsItsTrackAndPosition() {
        UUID track = new UUID(3, 3);
        Station station = Station.migrated(track, 90_000, 0f);
        assertEquals(track, station.currentTrack());
        assertTrue(station.playback().isPaused());
        assertEquals(90_000, station.playback().getCurrentPositionMs(PlaybackState.nowMs()));
    }
}
