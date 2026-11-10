package com.metrovoc.phonon.client;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.audio.Receiver;
import com.metrovoc.phonon.block.SpeakerBlockEntity;
import com.metrovoc.phonon.client.audio.StreamingAudioStream;
import com.metrovoc.phonon.platform.PlatformHelper;
import com.metrovoc.phonon.config.PhononClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Client-thread owner of speaker playback and stream leases.
 */
public final class ClientSpeakerManager {
    private static final ClientSpeakerManager INSTANCE = new ClientSpeakerManager();
    private static final long SAME_MOMENT_MS = 250;

    private final Map<Receiver, PlaybackState> speakers = new HashMap<>();
    private final Map<Receiver, Float> speakerVolumes = new HashMap<>();
    private final Map<Receiver, Long> activeStreams = new HashMap<>();
    private boolean holdNowPlaying;
    private Receiver heldNowPlaying;
    private final Set<Receiver> buffering = new HashSet<>();
    private final Set<Receiver> failed = new HashSet<>();
    private final Map<UUID, BoomboxPlace> boomboxes = new HashMap<>();

    /** Which entity has a boombox, and where it last was in case that entity isn't loaded here. */
    private static final class BoomboxPlace {
        private int entityId;
        private Vec3 lastSeen;

        private BoomboxPlace(int entityId, Vec3 lastSeen) {
            this.entityId = entityId;
            this.lastSeen = lastSeen;
        }
    }

    private ClientSpeakerManager() {}

    public static ClientSpeakerManager getInstance() {
        return INSTANCE;
    }

    /** A boombox changed hands without its sound changing, so this never touches playback. */
    public void placeBoombox(UUID id, int entityId, Vec3 position) {
        BoomboxPlace place = boomboxes.get(id);
        if (place == null) {
            boomboxes.put(id, new BoomboxPlace(entityId, position));
        } else {
            place.entityId = entityId;
            place.lastSeen = position;
        }
    }

    public void removeBoombox(UUID id) {
        boomboxes.remove(id);
        removeSpeaker(new Receiver.Boombox(id));
    }

    /** Whether this speaker or boombox still exists here. */
    public boolean exists(Receiver receiver) {
        return switch (receiver) {
            case Receiver.Block block -> Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockEntity(block.pos()) instanceof SpeakerBlockEntity;
            case Receiver.Boombox boombox -> boomboxes.containsKey(boombox.id());
        };
    }

    /** Where the sound comes from right now. */
    public Vec3 sourcePosition(Receiver receiver) {
        return switch (receiver) {
            case Receiver.Block block -> Vec3.atCenterOf(block.pos());
            case Receiver.Boombox boombox -> boomboxPosition(boombox.id());
        };
    }

    private Vec3 boomboxPosition(UUID id) {
        Minecraft minecraft = Minecraft.getInstance();
        BoomboxPlace place = boomboxes.get(id);
        if (place == null) {
            return minecraft.player == null ? Vec3.ZERO : minecraft.player.position();
        }
        Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(place.entityId);
        if (entity != null) {
            place.lastSeen = entity == minecraft.player
                ? entity.getEyePosition()
                : entity.position().add(0, entity.getBbHeight() / 2, 0);
        }
        return place.lastSeen;
    }

    /** Carried by the listener: right at their ears, so no wall comes between them. */
    public boolean carriedHere(Receiver receiver) {
        Minecraft minecraft = Minecraft.getInstance();
        return receiver instanceof Receiver.Boombox boombox && minecraft.player != null
            && boomboxes.containsKey(boombox.id()) && boomboxes.get(boombox.id()).entityId == minecraft.player.getId();
    }

    public void updateSpeaker(Receiver pos, PlaybackState playback, float volume) {
        volume = sanitizeVolume(volume);
        speakerVolumes.put(pos, volume);
        PlaybackState previous = speakers.get(pos);

        if (sameMoment(previous, playback)) {
            PlatformHelper.INSTANCE.setAudioVolume(pos, volume);
            return;
        }

        stopAndRelease(pos);

        if (playback.isPlaying()) {
            speakers.put(pos, playback);
            startPlayback(pos, playback, volume);
            if (previous == null || !Objects.equals(previous.resourceId(), playback.resourceId())) {
                announce(pos, playback.resourceId());
            }
        } else if (playback.isPaused()) {
            speakers.put(pos, playback);
        } else {
            speakers.remove(pos);
        }
    }

    /** Each update measures the clock anew, so one that only repeats what's playing mustn't restart the audio. */
    private static boolean sameMoment(PlaybackState previous, PlaybackState next) {
        return previous == null
            ? next.isStopped()
            : Objects.equals(previous.resourceId(), next.resourceId()) && previous.speed() == next.speed()
                && Math.abs(previous.getCurrentPositionMs() - next.getCurrentPositionMs()) < SAME_MOMENT_MS;
    }

    /** The same "Now Playing" line a jukebox shows, for players close enough to hear it. */
    private void announce(Receiver pos, UUID resourceId) {
        Minecraft minecraft = Minecraft.getInstance();
        double range = PhononClientConfig.getMaxAudioDistance();
        if (minecraft.player == null || minecraft.player.position().distanceToSqr(sourcePosition(pos)) > range * range) {
            return;
        }
        if (holdNowPlaying) {
            heldNowPlaying = pos;
        } else {
            showNowPlaying(resourceId);
        }
    }

    private static void showNowPlaying(UUID resourceId) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientAudioManager.getInstance().getResource(resourceId).ifPresent(resource ->
            //? if >=26.2 {
            minecraft.gui.hud.setNowPlaying(Component.literal(resource.name())));
            //?} else
            /*minecraft.gui.setNowPlaying(Component.literal(resource.name())));*/
    }

    /** The speaker screen covers the line, so tracks started behind it announce when it closes. */
    public void holdNowPlaying() {
        holdNowPlaying = true;
    }

    public void releaseNowPlaying() {
        holdNowPlaying = false;
        PlaybackState held = heldNowPlaying == null ? null : speakers.get(heldNowPlaying);
        heldNowPlaying = null;
        if (held != null && held.isPlaying()) {
            showNowPlaying(held.resourceId());
        }
    }

    public boolean isBuffering(Receiver pos) {
        return buffering.contains(pos);
    }

    public boolean hasFailed(Receiver pos) {
        return failed.contains(pos);
    }

    /** Ends every lease of a failed transfer, including speakers still waiting for their first audio. */
    public void streamFailed(long streamId) {
        for (Map.Entry<Receiver, Long> entry : Map.copyOf(activeStreams).entrySet()) {
            if (entry.getValue() == streamId) {
                Receiver pos = entry.getKey();
                stopAndRelease(pos);
                failed.add(pos);
            }
        }
    }

    private void stopAndRelease(Receiver pos) {
        buffering.remove(pos);
        failed.remove(pos);
        PlatformHelper.INSTANCE.stopAudio(pos);
        Long streamId = activeStreams.remove(pos);
        if (streamId != null) {
            StreamingAudioManager.getInstance().release(streamId);
        }
    }

    private void startPlayback(Receiver pos, PlaybackState playback, float volume) {
        UUID resourceId = playback.resourceId();
        if (resourceId == null) {
            return;
        }

        if (AudioCache.getInstance().isCached(resourceId)) {
            PlatformHelper.INSTANCE.playAudio(pos, playback, resourceId, volume);
            return;
        }

        long positionMs = playback.getCurrentPositionMs();
        StreamingAudioManager.Acquisition acquisition = StreamingAudioManager.getInstance()
            .acquire(resourceId, positionMs);
        long streamId = acquisition.session().getStreamId();
        activeStreams.put(pos, streamId);
        buffering.add(pos);

        StreamingAudioManager.getInstance().addReadyCallback(streamId, session -> {
            PlaybackState current = speakers.get(pos);
            Long activeStream = activeStreams.get(pos);
            if (current == null || !current.isPlaying() || !resourceId.equals(current.resourceId())
                || !Objects.equals(activeStream, streamId)) {
                return;
            }

            buffering.remove(pos);
            float currentVolume = speakerVolumes.getOrDefault(pos, 0.5f);
            long currentPosition = current.getCurrentPositionMs();
            startStreamingPlayback(pos, streamId, currentPosition, currentVolume);
        });

        if (acquisition.requestRequired()) {
            PlatformHelper.INSTANCE.requestAudioFromServer(streamId, resourceId, positionMs);
            Phonon.LOGGER.info("Requested audio stream {} for {} at {}ms", streamId, resourceId, positionMs);
        }
    }

    private void startStreamingPlayback(Receiver pos, long streamId, long positionMs, float volume) {
        StreamingAudioStream stream = StreamingAudioManager.getInstance().createStream(streamId, positionMs);
        if (stream == null) {
            Phonon.LOGGER.error("Failed to create decoder for audio stream {}", streamId);
            failed.add(pos);
            return;
        }
        PlatformHelper.INSTANCE.playStreamingAudio(pos, stream, volume);
    }

    /** Restarts a speaker at the shared clock's position, after its audio fell out of step. */
    public void resync(Receiver pos) {
        PlaybackState state = speakers.get(pos);
        if (state != null && state.isPlaying()) {
            Phonon.LOGGER.debug("Resyncing speaker at {}", pos);
            stopAndRelease(pos);
            startPlayback(pos, state, speakerVolumes.getOrDefault(pos, 0.5f));
        }
    }

    public void updateVolume(Receiver pos, float volume) {
        volume = sanitizeVolume(volume);
        speakerVolumes.put(pos, volume);
        PlatformHelper.INSTANCE.setAudioVolume(pos, volume);
    }

    private static float sanitizeVolume(float volume) {
        return Float.isFinite(volume)
            ? Math.max(0.0f, Math.min(1.0f, volume))
            : 0.5f;
    }

    public Optional<PlaybackState> getSpeakerState(Receiver pos) {
        return Optional.ofNullable(speakers.get(pos));
    }

    public float getSpeakerVolume(Receiver pos) {
        return speakerVolumes.getOrDefault(pos, 0.5f);
    }

    public void removeSpeaker(Receiver pos) {
        speakers.remove(pos);
        speakerVolumes.remove(pos);
        stopAndRelease(pos);
    }

    public void clear() {
        for (Receiver pos : List.copyOf(speakers.keySet())) {
            stopAndRelease(pos);
        }
        PlatformHelper.INSTANCE.stopAllAudio();
        speakers.clear();
        speakerVolumes.clear();
        boomboxes.clear();
        StreamingAudioManager.getInstance().clear();
    }

    /** Restores both cached and in-flight playback after F3+T. */
    public void onResourcesReloaded() {
        for (Map.Entry<Receiver, PlaybackState> entry : Map.copyOf(speakers).entrySet()) {
            if (!entry.getValue().isPlaying()) {
                continue;
            }
            Receiver pos = entry.getKey();
            float volume = speakerVolumes.getOrDefault(pos, 0.5f);
            stopAndRelease(pos);
            startPlayback(pos, entry.getValue(), volume);
        }
    }
}
