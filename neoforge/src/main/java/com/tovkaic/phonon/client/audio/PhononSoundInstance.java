package com.tovkaic.phonon.client.audio;

import com.tovkaic.phonon.audio.PlaybackState;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.client.resources.sounds.WeighedSoundEvents;
import net.minecraft.client.resources.sounds.Sound;

import java.util.UUID;

public class PhononSoundInstance extends AbstractTickableSoundInstance {
    private final UUID resourceId;
    private final BlockPos pos;
    private PlaybackState playbackState;

    public PhononSoundInstance(UUID resourceId, BlockPos pos, PlaybackState initialState) {
        super(
            ResourceLocation.fromNamespaceAndPath("phonon", "dynamic/" + resourceId),
            SoundSource.RECORDS,
            net.minecraft.util.RandomSource.create()
        );
        this.resourceId = resourceId;
        this.pos = pos;
        this.playbackState = initialState;
        
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;
        this.looping = false;
        this.delay = 0;
        
        updateState(initialState);
    }

    public void updateState(PlaybackState state) {
        this.playbackState = state;
        this.volume = state.volume();
        // Pitch is usually 1.0 for music
        this.pitch = 1.0f;
    }

    @Override
    public void tick() {
        if (!playbackState.playing()) {
            this.stop();
        }
    }
    
    public UUID getResourceId() {
        return resourceId;
    }
    
    public long getStartTime() {
        return playbackState.startTimeMs();
    }

    @Override
    public net.minecraft.client.resources.sounds.WeighedSoundEvents resolve(net.minecraft.client.sounds.SoundManager manager) {
        net.minecraft.client.resources.sounds.Sound sound = new net.minecraft.client.resources.sounds.Sound(
            this.getLocation().getPath(),
            net.minecraft.util.valueproviders.ConstantFloat.of(1.0f),
            net.minecraft.util.valueproviders.ConstantFloat.of(1.0f),
            1,
            net.minecraft.client.resources.sounds.Sound.Type.SOUND_EVENT,
            true,
            false,
            16
        );
        
        net.minecraft.client.resources.sounds.WeighedSoundEvents weighedSoundEvents = new net.minecraft.client.resources.sounds.WeighedSoundEvents(
            this.getLocation(),
            null // subtitle
        );
        weighedSoundEvents.addSound(sound);
        
        this.sound = sound;
        return weighedSoundEvents;
    }
}
