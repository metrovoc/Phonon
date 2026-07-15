package com.metrovoc.phonon.client.audio;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.block.SpeakerBlockEntity;
import com.metrovoc.phonon.client.ClientSpeakerManager;
import com.metrovoc.phonon.config.PhononClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class SpeakerSoundInstance extends AbstractTickableSoundInstance {

    private static final Identifier DUMMY_SOUND_LOCATION =
        Identifier.fromNamespaceAndPath(Constants.MOD_ID, "speaker");
    private static final Sound DUMMY_SOUND = new Sound(
        DUMMY_SOUND_LOCATION,
        ConstantFloat.of(1.0f),
        ConstantFloat.of(1.0f),
        1,
        Sound.Type.FILE,
        true,
        false,
        0
    );

    private static final float REFERENCE_DISTANCE = 4.0f;
    private static final float AIR_ABSORPTION_HALF_DISTANCE = 32.0f;
    private static final float AIR_ABSORPTION_FACTOR = 0.9f;
    private static final int NEAR_OCCLUSION_INTERVAL = 6;
    private static final int FAR_OCCLUSION_INTERVAL = 12;
    private static final int MAX_PENETRATION_LAYERS = 8;
    private static final float MIN_TRANSMISSION = 0.05f;
    private static final float SAMPLE_OFFSET = 0.5f;
    private static final float CENTER_WEIGHT = 0.6f;
    private static final float SIDE_WEIGHT = 0.1f;
    private static final float OCCLUSION_LERP_FACTOR = 0.5f;

    private final AudioStream stream;
    private final BlockPos sourcePos;
    private final Vec3 sourceVec;
    private float baseVolume;

    private int tickCounter;
    private float occlusionFactor = 1.0f;
    private float targetOcclusion = 1.0f;
    private final Map<BlockState, Float> opacityCache = new HashMap<>();

    // Minimum volume to ensure SoundEngine creates audio channel
    // (Minecraft skips play() when volume is exactly 0)
    private static final float MIN_INITIAL_VOLUME = 0.0001f;

    public SpeakerSoundInstance(AudioStream stream, BlockPos pos, float volume) {
        super(
            SoundEvent.createVariableRangeEvent(DUMMY_SOUND_LOCATION),
            SoundSource.BLOCKS,
            SoundInstance.createUnseededRandom()
        );

        this.stream = stream;
        this.sourcePos = pos;
        this.sourceVec = Vec3.atCenterOf(pos);
        this.baseVolume = volume;

        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;
        // Ensure initial volume > 0 so SoundEngine creates audio channel
        this.volume = Math.max(volume, MIN_INITIAL_VOLUME);
        this.attenuation = Attenuation.NONE;
        this.tickCounter = Math.floorMod(pos.hashCode(), NEAR_OCCLUSION_INTERVAL);
    }

    @Override
    public Sound getSound() {
        return DUMMY_SOUND;
    }

    @Override
    public WeighedSoundEvents resolve(net.minecraft.client.sounds.SoundManager soundManager) {
        this.sound = DUMMY_SOUND;
        return new WeighedSoundEvents(DUMMY_SOUND_LOCATION, null);
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        return CompletableFuture.completedFuture(this.stream);
    }

    @Override
    public void tick() {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        var player = mc.player;

        if (level == null || player == null) {
            super.stop();
            return;
        }

        if (!(level.getBlockEntity(sourcePos) instanceof SpeakerBlockEntity)) {
            super.stop();
            return;
        }

        var state = ClientSpeakerManager.getInstance().getSpeakerState(sourcePos);
        if (state.isEmpty() || !state.get().isPlaying()) {
            super.stop();
            return;
        }

        updateAcoustics(level, player.getEyePosition());
    }

    private void updateAcoustics(Level level, Vec3 listenerPos) {
        double distanceSquared = listenerPos.distanceToSqr(sourceVec);
        float maxDistance = (float) PhononClientConfig.getMaxAudioDistance();

        if (distanceSquared > maxDistance * maxDistance) {
            this.volume = 0;
            tickCounter = FAR_OCCLUSION_INTERVAL;
            return;
        }

        double dist = Math.sqrt(distanceSquared);

        float geometric = (float) Math.min(1.0, REFERENCE_DISTANCE / Math.max(dist, 0.1));
        float airAbsorption = (float) Math.pow(AIR_ABSORPTION_FACTOR, dist / AIR_ABSORPTION_HALF_DISTANCE);
        float distanceAttenuation = geometric * airAbsorption;

        tickCounter++;
        int occlusionInterval = dist <= 16.0 ? NEAR_OCCLUSION_INTERVAL : FAR_OCCLUSION_INTERVAL;
        if (tickCounter >= occlusionInterval) {
            tickCounter = 0;
            targetOcclusion = calculateOcclusion(level, sourceVec, listenerPos);
        }

        occlusionFactor = Mth.lerp(OCCLUSION_LERP_FACTOR, occlusionFactor, targetOcclusion);
        this.volume = baseVolume * distanceAttenuation * occlusionFactor;
    }

    private float calculateOcclusion(Level level, Vec3 source, Vec3 listener) {
        Vec3 forward = listener.subtract(source).normalize();
        Vec3 refUp = Math.abs(forward.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = forward.cross(refUp).normalize();
        Vec3 realUp = right.cross(forward).normalize();

        float centerTransmission = traceRay(level, source, listener);
        float rightTransmission = traceRay(level, source.add(right.scale(SAMPLE_OFFSET)), listener);
        float leftTransmission = traceRay(level, source.add(right.scale(-SAMPLE_OFFSET)), listener);
        float upTransmission = traceRay(level, source.add(realUp.scale(SAMPLE_OFFSET)), listener);
        float downTransmission = traceRay(level, source.add(realUp.scale(-SAMPLE_OFFSET)), listener);

        return centerTransmission * CENTER_WEIGHT
             + rightTransmission * SIDE_WEIGHT
             + leftTransmission * SIDE_WEIGHT
             + upTransmission * SIDE_WEIGHT
             + downTransmission * SIDE_WEIGHT;
    }

    private float traceRay(Level level, Vec3 start, Vec3 end) {
        TransmissionTrace trace = new TransmissionTrace(level);
        return BlockGetter.traverseBlocks(
            start,
            end,
            trace,
            (context, pos) -> context.visit(pos),
            context -> context.transmission
        );
    }

    private float getBlockSoundOpacity(BlockState state) {
        return opacityCache.computeIfAbsent(state, this::calculateBlockSoundOpacity);
    }

    private float calculateBlockSoundOpacity(BlockState state) {
        if (state.isAir()) {
            return 0.0f;
        }

        if (!state.canOcclude()) {
            return 0.2f;
        }

        @SuppressWarnings("deprecation")
        SoundType soundType = state.getSoundType();

        if (soundType == SoundType.WOOL) return 0.8f;
        if (soundType == SoundType.SNOW) return 0.9f;
        if (soundType == SoundType.GLASS) return 0.2f;
        if (soundType == SoundType.METAL) return 0.6f;
        if (soundType == SoundType.WOOD) return 0.5f;
        if (soundType == SoundType.STONE) return 0.8f;
        if (soundType == SoundType.SAND) return 0.7f;

        return 0.5f;
    }

    private final class TransmissionTrace {
        private final Level level;
        private int layers;
        private float transmission = 1.0f;

        private TransmissionTrace(Level level) {
            this.level = level;
        }

        private Float visit(BlockPos pos) {
            if (pos.equals(sourcePos)) {
                return null;
            }

            BlockState state = level.getBlockState(pos);
            if (!state.canOcclude() && state.getCollisionShape(level, pos).isEmpty()) {
                return null;
            }

            float opacity = getBlockSoundOpacity(state);
            if (opacity <= 0) {
                return null;
            }

            transmission *= 1.0f - opacity;
            layers++;
            return layers >= MAX_PENETRATION_LAYERS || transmission <= MIN_TRANSMISSION
                ? transmission
                : null;
        }
    }

    public BlockPos getPosition() {
        return sourcePos;
    }

    public void setVolume(float newVolume) {
        this.baseVolume = newVolume;
    }
}
