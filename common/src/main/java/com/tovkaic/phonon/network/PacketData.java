package com.tovkaic.phonon.network;

import com.tovkaic.phonon.audio.AudioResource;
import com.tovkaic.phonon.audio.PlaybackState;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.UUID;

/**
 * Network packet data structures.
 * Simple, flat, no inheritance nonsense.
 */
public class PacketData {

    /**
     * Sync all audio resources to client (sent on join).
     */
    public record SyncAudioResources(List<AudioResource> resources) {}

    /**
     * Sync speaker playback state to clients.
     */
    public record SyncSpeakerState(BlockPos pos, PlaybackState playback) {}

    /**
     * Request audio file from server.
     */
    public record RequestAudio(UUID resourceId) {}

    /**
     * Audio file chunk transfer (server -> client).
     */
    public record AudioChunk(
        UUID resourceId,
        int chunkIndex,
        int totalChunks,
        byte[] data
    ) {}
}
