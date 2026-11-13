package com.metrovoc.phonon.client.audio;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.audio.Receiver;
import com.metrovoc.phonon.client.ClientSpeakerManager;
import com.metrovoc.phonon.mixin.ChannelAccessor;
import com.metrovoc.phonon.mixin.SoundEngineAccessor;
import com.metrovoc.phonon.mixin.SoundManagerAccessor;
import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.metrovoc.phonon.platform.PlatformHelper;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

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
    private final Receiver receiver;
    private BlockPos sourcePos;
    private Vec3 sourceVec;
    private float baseVolume;

    private int tickCounter;
    private float occlusionFactor = 1.0f;
    private float targetOcclusion = 1.0f;
    private final Map<BlockState, Float> opacityCache = new HashMap<>();

    private static final int FADE_TICKS = 4;
    private static final float WATER_GAIN_HF = 0.12f;
    private static final float NOTE_RATE = 0.3f;
    private static final double NOTE_RANGE_SQUARED = 32.0 * 32.0;
    private static final double DANCE_RANGE = 3.46;
    private static final boolean SOUND_PHYSICS_LOADED = PlatformHelper.INSTANCE.isModLoaded("sound_physics_remastered");

    private volatile int channelSource;
    private LowPass lowPass;
    private int age;
    private int envelopeTicks;
    private boolean fadingOut;
    private float audibleVolume;

    // Minimum volume to ensure SoundEngine creates audio channel
    // (Minecraft skips play() when volume is exactly 0)
    private static final float MIN_INITIAL_VOLUME = 0.0001f;

    public SpeakerSoundInstance(AudioStream stream, Receiver receiver, float volume) {
        super(
            SoundEvent.createVariableRangeEvent(DUMMY_SOUND_LOCATION),
            SoundSource.RECORDS,
            SoundInstance.createUnseededRandom()
        );

        this.stream = stream;
        this.receiver = receiver;
        this.baseVolume = volume;
        moveToSource();
        // Ensure initial volume > 0 so SoundEngine creates audio channel
        this.volume = MIN_INITIAL_VOLUME;
        this.attenuation = Attenuation.NONE;
        this.tickCounter = Math.floorMod(receiver.hashCode(), NEAR_OCCLUSION_INTERVAL);
    }

    private void moveToSource() {
        ClientSpeakerManager speakers = ClientSpeakerManager.getInstance();
        sourceVec = speakers.sourcePosition(receiver);
        sourcePos = BlockPos.containing(sourceVec);
        // A boombox the listener carries plays at their ears. Placing it at their eyes each tick would leave it
        // just off to one side of the per-frame listener while they walk, which pans it hard left or right.
        boolean carried = speakers.carriedHere(receiver);
        Vec3 played = carried ? Vec3.ZERO : sourceVec;
        x = played.x;
        y = played.y;
        z = played.z;
        if (carried != relative) {
            relative = carried;
            queueRelative();
        }
    }

    /**
     * Switches a playing sound between the world and the listener's ears, for a boombox picked up or put down. It goes
     * through the sound's own queue, so it lands before the engine's position update for this tick and after the last.
     * A sound not playing yet needs nothing: the engine reads {@link #isRelative()} when it starts it.
     */
    private void queueRelative() {
        SoundEngine engine = ((SoundManagerAccessor) Minecraft.getInstance().getSoundManager()).phonon$getSoundEngine();
        ChannelAccess.ChannelHandle handle = ((SoundEngineAccessor) engine).phonon$getInstanceToChannel().get(this);
        if (handle != null) {
            boolean atEars = relative;
            handle.execute(channel -> channel.setRelative(atEars));
        }
    }

    @Override
    public Sound getSound() {
        return DUMMY_SOUND;
    }

    @Override
    //? if >=26.3 {
    public WeighedSoundEvents getOrResolve(net.minecraft.client.sounds.SoundManager soundManager) {
    //?} else
    /*public WeighedSoundEvents resolve(net.minecraft.client.sounds.SoundManager soundManager) {*/
        this.sound = DUMMY_SOUND;
        return new WeighedSoundEvents(DUMMY_SOUND_LOCATION, null);
    }

    /** The audio this sound plays; each loader hands it to the sound engine in place of a sound file. */
    public AudioStream stream() {
        return stream;
    }

    @Override
    public void tick() {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        var player = mc.player;

        if (level == null || player == null) {
            finish();
            return;
        }

        age++;
        var state = ClientSpeakerManager.getInstance().getSpeakerState(receiver);
        boolean wanted = ClientSpeakerManager.getInstance().exists(receiver)
            && state.isPresent() && state.get().isPlaying();
        if (!wanted) {
            fadingOut = true;
        }

        envelopeTicks += fadingOut ? -1 : 1;
        envelopeTicks = Math.min(envelopeTicks, FADE_TICKS);
        if (envelopeTicks <= 0 && fadingOut) {
            finish();
            return;
        }

        moveToSource();
        updateAcoustics(level, player.getEyePosition(), player.isUnderWater());
        if (!fadingOut) {
            reactWorld(level, player.position(), state.get());
        }
    }

    private void finish() {
        audibleVolume = 0;
        if (lowPass != null) {
            lowPass.release();
            lowPass = null;
        }
        super.stop();
    }

    /** Ends the sound after a short fade, so controls don't click. */
    public void fadeOut() {
        fadingOut = true;
    }

    public void stopNow() {
        finish();
    }

    /** Called when the sound engine has given this sound its OpenAL source. */
    public void attachChannel(Channel channel) {
        channelSource = ((ChannelAccessor) channel).phonon$getSource();
    }

    private void updateMuffle(boolean underWater) {
        int source = channelSource;
        if (source == 0 || SOUND_PHYSICS_LOADED) {
            return;
        }
        if (lowPass == null) {
            lowPass = new LowPass(source);
        }
        float gainHf = occlusionFactor * occlusionFactor;
        if (underWater) {
            gainHf *= WATER_GAIN_HF;
        }
        lowPass.set(Math.max(0.02f, gainHf));
    }

    /** Notes rise with the music's loudness, and parrots near the speaker dance. */
    private void reactWorld(Level level, Vec3 listener, PlaybackState state) {
        if (listener.distanceToSqr(sourceVec) > NOTE_RANGE_SQUARED) {
            return;
        }
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(sourcePos).inflate(DANCE_RANGE))) {
            entity.setRecordPlayingNearby(sourcePos, true);
        }
        StreamTap tap = tap();
        RandomSource random = level.getRandom();
        float loudness = tap == null ? 0 : tap.levelAt(state.getCurrentPositionMs());
        if (random.nextFloat() < loudness * loudness * NOTE_RATE && level.getBlockState(sourcePos.above()).isAir()) {
            level.addParticle(ParticleTypes.NOTE,
                sourceVec.x + (random.nextDouble() - 0.5) * 0.4,
                sourcePos.getY() + 1.2,
                sourceVec.z + (random.nextDouble() - 0.5) * 0.4,
                random.nextInt(25) / 24.0, 0, 0);
        }
    }

    /** Ticks since the sound started. */
    public int age() {
        return age;
    }

    /** Volume reaching the listener right now, after distance, walls and fades. */
    public float audibleVolume() {
        return audibleVolume;
    }

    public float muffleGainHf() {
        return lowPass == null ? 1 : lowPass.gainHf();
    }

    private void updateAcoustics(Level level, Vec3 listenerPos, boolean underWater) {
        double distanceSquared = listenerPos.distanceToSqr(sourceVec);
        float maxDistance = ClientSpeakerManager.getInstance().hearingDistance();

        if (distanceSquared > maxDistance * maxDistance) {
            this.volume = 0;
            audibleVolume = 0;
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
            targetOcclusion = relative || !ClientSpeakerManager.getInstance().muffleBehindWalls()
                ? 1.0f
                : calculateOcclusion(level, sourceVec, listenerPos);
        }

        occlusionFactor = Mth.lerp(OCCLUSION_LERP_FACTOR, occlusionFactor, targetOcclusion);
        float envelope = (float) envelopeTicks / FADE_TICKS;
        // Muffling already carries most of what a wall does, so bass still comes through it
        float wallGain = LowPass.isActive() && !SOUND_PHYSICS_LOADED ? (float) Math.sqrt(occlusionFactor) : occlusionFactor;
        audibleVolume = baseVolume * distanceAttenuation * wallGain;
        this.volume = Math.max(audibleVolume * envelope, MIN_INITIAL_VOLUME);
        updateMuffle(underWater);
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
        TransmissionTrace trace = new TransmissionTrace(level, start, end);
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
        private final Vec3 start;
        private final Vec3 end;
        private int layers;
        private float transmission = 1.0f;

        private TransmissionTrace(Level level, Vec3 start, Vec3 end) {
            this.level = level;
            this.start = start;
            this.end = end;
        }

        private Float visit(BlockPos pos) {
            if (pos.equals(sourcePos)) {
                return null;
            }

            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                return null;
            }
            // Only what the ray passes through: carpets, slabs and fences in its cell but beside it don't count
            // A solid block without a collision box still stands in the way, and so does powder snow, which newer
            // versions no longer count as solid
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty() && (state.canOcclude() || state.getBlock() instanceof PowderSnowBlock)) {
                shape = Shapes.block();
            }
            if (shape.isEmpty() || shape.clip(start, end, pos) == null) {
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

    public StreamTap tap() {
        if (stream instanceof PhononAudioStream cached) {
            return cached.tap();
        }
        return stream instanceof StreamingAudioStream streaming ? streaming.tap() : null;
    }

    public void setVolume(float newVolume) {
        this.baseVolume = newVolume;
    }
}
