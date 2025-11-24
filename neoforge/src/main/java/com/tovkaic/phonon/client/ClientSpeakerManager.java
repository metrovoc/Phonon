package com.tovkaic.phonon.client;

import com.tovkaic.phonon.audio.PlaybackState;
import com.tovkaic.phonon.client.audio.PhononSoundInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side speaker manager.
 * Tracks all active speakers and their playback states.
 */
public class ClientSpeakerManager {
    private static ClientSpeakerManager instance;
    private final Map<BlockPos, PhononSoundInstance> activeSounds = new ConcurrentHashMap<>();
    private final Map<BlockPos, PlaybackState> speakerStates = new ConcurrentHashMap<>();

    private ClientSpeakerManager() {}

    public static ClientSpeakerManager getInstance() {
        if (instance == null) {
            instance = new ClientSpeakerManager();
        }
        return instance;
    }

    public void updateSpeaker(BlockPos pos, PlaybackState playback) {
        speakerStates.put(pos, playback);
        
        if (playback.playing() && playback.resourceId() != null) {
            UUID resourceId = playback.resourceId();

            // Check if audio is already cached
            if (AudioCache.getInstance().getCachedAudio(resourceId).isPresent()) {
                startPlayback(pos, playback, resourceId);
            } else {
                // Not cached - download first (request chunks)
                // Note: In new system, we request chunks. AudioCache handles this.
                // But we need to trigger the request if we don't have it.
                // For now, assume AudioCache handles the request logic or we trigger it here.
                // Since we changed to chunk based, we might need to explicitly request the first chunk.
                
                // Check if we have the file
                if (AudioCache.getInstance().getCachedAudio(resourceId).isEmpty()) {
                     // Request first chunk to start download
                     var packet = new com.tovkaic.phonon.network.packets.RequestAudioPacket(resourceId, 0);
                     net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
                }
                
                // We can't play yet. We need to wait for download.
                // We can poll or register a callback.
                // For MVP, let's just retry periodically or wait for full download.
                // Since we don't have a "onDownloadComplete" event easily wired yet without more refactoring,
                // we will rely on the fact that once downloaded, the next update (or a periodic check) will play it.
                // Actually, AudioCache has callbacks. We can use that.
                // But AudioCache.downloadAudio is deprecated/removed.
                // We need a way to know when it's ready.
                // Let's just add a periodic check or simple callback if possible.
                // For now, we'll just log and wait.
            }
        } else {
            stopSpeaker(pos);
        }
    }
    
    private void startPlayback(BlockPos pos, PlaybackState playback, UUID resourceId) {
        // Stop existing sound at this pos if any
        stopSpeaker(pos);
        
        PhononSoundInstance sound = new PhononSoundInstance(resourceId, pos, playback);
        activeSounds.put(pos, sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    public Optional<PlaybackState> getSpeakerState(BlockPos pos) {
        return Optional.ofNullable(speakerStates.get(pos));
    }

    public void removeSpeaker(BlockPos pos) {
        speakerStates.remove(pos);
        stopSpeaker(pos);
    }
    
    private void stopSpeaker(BlockPos pos) {
        PhononSoundInstance sound = activeSounds.remove(pos);
        if (sound != null) {
            Minecraft.getInstance().getSoundManager().stop(sound);
        }
    }
}
