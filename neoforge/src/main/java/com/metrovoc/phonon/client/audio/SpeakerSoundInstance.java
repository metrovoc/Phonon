package com.metrovoc.phonon.client.audio;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.block.SpeakerBlockEntity;
import com.metrovoc.phonon.client.ClientSpeakerManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.CompletableFuture;

public class SpeakerSoundInstance extends AbstractTickableSoundInstance {

    private static final ResourceLocation DUMMY_SOUND_LOCATION =
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "speaker");
    private static final Sound DUMMY_SOUND = new Sound(
        DUMMY_SOUND_LOCATION,
        ConstantFloat.of(1.0f),
        ConstantFloat.of(1.0f),
        1,
        Sound.Type.FILE,
        true,
        false,
        64
    );

    private static final float REFERENCE_DISTANCE = 4.0f;
    private static final float MAX_DISTANCE = 96.0f;
    private static final float AIR_ABSORPTION_HALF_DISTANCE = 32.0f;
    private static final float AIR_ABSORPTION_FACTOR = 0.9f;
    private static final int RAYCAST_INTERVAL = 4;
    private static final int MAX_PENETRATION_LAYERS = 8;
    private static final float MIN_TRANSMISSION = 0.05f;
    private static final float SAMPLE_OFFSET = 0.5f;
    private static final float CENTER_WEIGHT = 0.6f;
    private static final float SIDE_WEIGHT = 0.1f;
    private static final float OCCLUSION_LERP_FACTOR = 0.5f;

    private final PhononAudioStream stream;
    private final BlockPos sourcePos;
    private final float baseVolume;

    private int tickCounter;
    private float occlusionFactor = 1.0f;
    private float targetOcclusion = 1.0f;

    public SpeakerSoundInstance(PhononAudioStream stream, BlockPos pos, float volume) {
        super(
            SoundEvent.createVariableRangeEvent(DUMMY_SOUND_LOCATION),
            SoundSource.BLOCKS,
            SoundInstance.createUnseededRandom()
        );

        this.stream = stream;
        this.sourcePos = pos;
        this.baseVolume = volume;

        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;

        this.looping = false;
        this.volume = volume;
        this.attenuation = Attenuation.NONE;
        this.delay = 0;

        this.tickCounter = pos.hashCode() % RAYCAST_INTERVAL;
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
        if (state.isEmpty() || !state.get().playing()) {
            super.stop();
            return;
        }

        updateAcoustics(level, player.getEyePosition());
    }

    private void updateAcoustics(Level level, Vec3 listenerPos) {
        Vec3 sourceVec = new Vec3(x, y, z);
        double dist = listenerPos.distanceTo(sourceVec);

        if (dist > MAX_DISTANCE) {
            this.volume = 0;
            return;
        }

        float geometric = (float) Math.min(1.0, REFERENCE_DISTANCE / Math.max(dist, 0.1));
        float airAbsorption = (float) Math.pow(AIR_ABSORPTION_FACTOR, dist / AIR_ABSORPTION_HALF_DISTANCE);
        float distanceAttenuation = geometric * airAbsorption;

        tickCounter++;
        if (tickCounter >= RAYCAST_INTERVAL) {
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
        float transmission = 1.0f;
        Vec3 current = start;

        for (int layer = 0; layer < MAX_PENETRATION_LAYERS && transmission > MIN_TRANSMISSION; layer++) {
            BlockHitResult hit = level.clip(new ClipContext(
                current,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                CollisionContext.empty()
            ));

            if (hit.getType() == HitResult.Type.MISS) {
                break;
            }

            BlockPos hitPos = hit.getBlockPos();
            BlockState blockState = level.getBlockState(hitPos);

            if (hitPos.equals(sourcePos)) {
                Vec3 direction = end.subtract(current).normalize();
                current = hit.getLocation().add(direction.scale(0.1));
                continue;
            }

            transmission *= 1.0f - getBlockSoundOpacity(blockState);

            Vec3 direction = end.subtract(current).normalize();
            current = hit.getLocation().add(direction.scale(0.1));
        }

        return transmission;
    }

    private float getBlockSoundOpacity(BlockState state) {
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

    public BlockPos getPosition() {
        return sourcePos;
    }

    public void setVolume(float newVolume) {
        this.volume = newVolume;
    }
}
