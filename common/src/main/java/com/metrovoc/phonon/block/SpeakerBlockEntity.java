package com.metrovoc.phonon.block;

import com.mojang.serialization.Codec;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.audio.Receiver;
import com.metrovoc.phonon.platform.PlatformHelper;
import com.metrovoc.phonon.station.StationListener;
import com.google.gson.JsonParser;
import com.metrovoc.phonon.client.ClientStations;
import com.metrovoc.phonon.station.Station;
import com.metrovoc.phonon.station.StationManager;
import com.metrovoc.phonon.server.SpeakerControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Speaker block entity, 存储播放状态和音量。
 * NBT 保存时"坍缩" - 只保存当前位置和 speed，不保存 anchorTime。
 */
public class SpeakerBlockEntity extends BlockEntity implements StationListener {
    private static final float DEFAULT_VOLUME = 0.5f;

    private static Supplier<BlockEntityType<SpeakerBlockEntity>> typeSupplier;
    private PlaybackState playback = PlaybackState.STOPPED;
    private float volume = DEFAULT_VOLUME;
    private Station ownStation = Station.ownedBySpeaker();
    private UUID tunedTo;
    private UUID ownerId;
    private String ownerName = "";
    private boolean resumeOwn;
    private int poweredSides;
    private int lastSignal;

    public static void setTypeSupplier(Supplier<BlockEntityType<SpeakerBlockEntity>> supplier) {
        typeSupplier = supplier;
    }

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(typeSupplier.get(), pos, state);
    }

    public PlaybackState getPlayback() {
        return playback;
    }

    public void setPlayback(PlaybackState playback) {
        boolean wasPlaying = this.playback.isPlaying();
        this.playback = playback;
        setChanged();

        if (level == null) {
            return;
        }
        // 同步 PLAYING blockstate 用于视觉效果 (灯光 + 动画)
        BlockState state = getBlockState();
        boolean playing = playback.isPlaying();
        if (state.getValue(SpeakerBlock.PLAYING) != playing) {
            level.setBlock(worldPosition, state.setValue(SpeakerBlock.PLAYING, playing), 3);
        }
        if (level.isClientSide()) {
            return;
        }
        if (playing != wasPlaying) {
            level.gameEvent(playing ? GameEvent.JUKEBOX_PLAY : GameEvent.JUKEBOX_STOP_PLAY, worldPosition,
                GameEvent.Context.of(getBlockState()));
            // Sculk sensors don't listen for the jukebox events; this is what a jukebox fires for them
            level.gameEvent(GameEvent.BLOCK_CHANGE, worldPosition, GameEvent.Context.of(getBlockState()));
        }
        updateComparators();
    }

    @Override
    public Receiver receiver() {
        return new Receiver.Block(worldPosition);
    }

    @Override
    public void broadcast(Object packet) {
        PlatformHelper.INSTANCE.sendToAllTracking(level, worldPosition, packet);
    }

    /** The station this speaker is tuned to, or its own private one. */
    @Override
    public Station station() {
        Station tuned = StationManager.getInstance().get(tunedTo);
        return tuned != null ? tuned : ownStation;
    }

    public void setOwner(UUID ownerId, String ownerName) {
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        setChanged();
    }

    public String getOwnerName() {
        return ownerName;
    }

    @Override
    public UUID getOwnerId() {
        return ownerId;
    }

    /** Listens to a shared station, or with null goes back to this speaker's own. */
    @Override
    public void tune(UUID stationId) {
        Station before = station();
        StationManager manager = StationManager.getInstance();
        manager.detach(before.id(), level.dimension(), worldPosition);
        boolean wasOnOwn = tunedTo == null;
        tunedTo = manager.get(stationId) != null ? stationId : null;
        if (tunedTo != null && wasOnOwn) {
            resumeOwn = ownStation.playback().isPlaying();
            ownStation.pause();
        } else if (tunedTo == null && resumeOwn) {
            ownStation.resume();
            resumeOwn = false;
        }
        setChanged();
        Station after = station();
        manager.attach(after.id(), level.dimension(), worldPosition);
        manager.changed(after);
    }

    /** 0 when stopped, otherwise 1 to 15 through the track, for building things that follow a song. */
    public int comparatorSignal() {
        if (playback.isStopped()) {
            return 0;
        }
        long duration = StationManager.durationMs(playback.resourceId());
        if (duration <= 0) {
            return 1;
        }
        long position = Math.min(playback.getCurrentPositionMs(), duration - 1);
        return 1 + (int) (position * 15 / duration);
    }

    public void updateComparators() {
        int signal = comparatorSignal();
        if (signal != lastSignal && level != null) {
            lastSignal = signal;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    /**
     * Acts on signals that just turned on, by side as seen facing the speaker:
     * left is previous, right is next, the back stops, and the rest toggle play and pause.
     */
    public void onNeighborSignals() {
        if (level == null || level.isClientSide()) {
            return;
        }
        Direction facing = getBlockState().getValue(SpeakerBlock.FACING);
        int powered = 0;
        for (Direction side : Direction.values()) {
            if (level.hasSignal(worldPosition.relative(side), side)) {
                powered |= 1 << side.ordinal();
            }
        }
        int rising = powered & ~poweredSides;
        if (powered != poweredSides) {
            poweredSides = powered;
            setChanged();
        }
        for (Direction side : Direction.values()) {
            if ((rising & 1 << side.ordinal()) == 0) {
                continue;
            }
            if (side == facing.getClockWise()) {
                SpeakerControl.step(this, -1);
            } else if (side == facing.getCounterClockWise()) {
                SpeakerControl.step(this, 1);
            } else if (side == facing.getOpposite()) {
                SpeakerControl.stop(this);
            } else {
                SpeakerControl.toggle(this);
            }
        }
    }

    @Override
    public float getVolume() {
        return volume;
    }

    @Override
    public void setVolume(float volume) {
        this.volume = sanitizeVolume(volume);
        setChanged();
    }

    private static float sanitizeVolume(float volume) {
        return Float.isFinite(volume)
            ? Math.max(0.0f, Math.min(1.0f, volume))
            : DEFAULT_VOLUME;
    }

    @Override
    public void setLevel(net.minecraft.world.level.Level level) {
        super.setLevel(level);
        if (level != null) {
            if (!level.isClientSide()) {
                StationManager manager = StationManager.getInstance();
                manager.addPrivate(ownStation);
                Station station = station();
                manager.attach(station.id(), level.dimension(), worldPosition);
                playback = station.playback();
            } else {
                // 客户端：区块加载时立即恢复播放
                com.metrovoc.phonon.client.ClientSpeakerManager.getInstance().updateSpeaker(receiver(), playback, volume);
                publishStationToClient();
            }
        }
    }

    @Override
    public void setRemoved() {
        // BlockEntity 被移除时清理状态
        if (level != null) {
            if (level.isClientSide()) {
                com.metrovoc.phonon.client.ClientSpeakerManager.getInstance().removeSpeaker(receiver());
            } else {
                StationManager manager = StationManager.getInstance();
                manager.detach(station().id(), level.dimension(), worldPosition);
                manager.removePrivate(ownStation);
            }
        }
        super.setRemoved();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return saveCustomOnly(provider);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);

        output.putFloat("volume", volume);
        output.putInt("poweredSides", poweredSides);
        output.putString("ownStation", ownStation.toJson().toString());
        output.putBoolean("resumeOwn", resumeOwn);
        output.putString("ownerName", ownerName);
        if (tunedTo != null) {
            output.putIntArray("tunedTo", UUIDUtil.uuidToIntArray(tunedTo));
        }
        if (ownerId != null) {
            output.putIntArray("ownerId", UUIDUtil.uuidToIntArray(ownerId));
        }

        // 保存时"坍缩": 计算当前位置，不保存 anchorTime
        if (playback.resourceId() != null) {
            output.putIntArray("resourceId", UUIDUtil.uuidToIntArray(playback.resourceId()));
            long now = PlaybackState.nowMs();
            long effectivePos = playback.getCurrentPositionMs(now);
            output.putLong("position", effectivePos);
            output.putFloat("speed", playback.speed());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);

        volume = sanitizeVolume(input.getFloatOr("volume", DEFAULT_VOLUME));
        poweredSides = input.getIntOr("poweredSides", 0);
        tunedTo = readUuid(input, "tunedTo");
        ownerId = readUuid(input, "ownerId");
        ownerName = input.getStringOr("ownerName", "");
        resumeOwn = input.getBooleanOr("resumeOwn", false);
        Station before = ownStation;
        boolean hasStation = false;
        try {
            String stationJson = input.getStringOr("ownStation", "");
            if (!stationJson.isEmpty()) {
                ownStation = Station.fromJson(JsonParser.parseString(stationJson).getAsJsonObject());
                hasStation = true;
            }
        } catch (RuntimeException e) {
            com.metrovoc.phonon.Phonon.LOGGER.warn("Ignoring a speaker's unreadable station data", e);
        }

        try {
            // 兼容旧格式: playing + startTime
            if (input.read("playing", Codec.BOOL).isPresent()) {
                loadLegacyFormat(input);
                if (playback.resourceId() != null) {
                    ownStation = Station.migrated(playback.resourceId(), playback.positionAtAnchorMs(), playback.speed());
                }
                relinkAfterLoad(before);
                return;
            }

            // 新格式: resourceId + position + speed
            if (input.getIntArray("resourceId").isEmpty()) {
                playback = PlaybackState.STOPPED;
                relinkAfterLoad(before);
                return;
            }

            UUID resourceId = readUuid(input, "resourceId");
            if (resourceId == null) {
                playback = PlaybackState.STOPPED;
                relinkAfterLoad(before);
                return;
            }

            long savedPos = input.getLongOr("position", 0L);
            float speed = input.getFloatOr("speed", 1.0f);

            // 读取时用当前时间作为新的 anchor
            long now = PlaybackState.nowMs();
            playback = new PlaybackState(resourceId, now, savedPos, speed);
            if (!hasStation) {
                ownStation = Station.migrated(resourceId, savedPos, speed);
            }
            relinkAfterLoad(before);

            // 如果此时 level 已存在且为客户端，立即同步
            if (level != null && level.isClientSide()) {
                com.metrovoc.phonon.client.ClientSpeakerManager.getInstance().updateSpeaker(receiver(), playback, volume);
            }
        } catch (Exception e) {
            playback = PlaybackState.STOPPED;
        }
    }

    /**
     * 兼容旧 NBT 格式 (playing + startTime)。
     */
    private void loadLegacyFormat(ValueInput input) {
        if (!input.getBooleanOr("playing", false)) {
            playback = PlaybackState.STOPPED;
            return;
        }

        if (input.getIntArray("resourceId").isEmpty()) {
            playback = PlaybackState.STOPPED;
            return;
        }

        UUID resourceId = readUuid(input, "resourceId");
        if (resourceId == null) {
            playback = PlaybackState.STOPPED;
            return;
        }

        long startTime = input.getLongOr("startTime", System.currentTimeMillis());
        long elapsed = System.currentTimeMillis() - startTime;
        long now = PlaybackState.nowMs();

        // 转换为新格式: anchor=now, position=elapsed, speed=1.0
        playback = new PlaybackState(resourceId, now, Math.max(0, elapsed), 1.0f);
    }

    /** New data can arrive after this speaker joined its level, as with setblock or a client update. */
    private void relinkAfterLoad(Station before) {
        if (level == null) {
            return;
        }
        if (level.isClientSide()) {
            publishStationToClient();
        } else if (before != ownStation) {
            StationManager manager = StationManager.getInstance();
            manager.detach(before.id(), level.dimension(), worldPosition);
            manager.removePrivate(before);
            manager.addPrivate(ownStation);
            manager.attach(station().id(), level.dimension(), worldPosition);
            playback = station().playback();
        }
    }

    /** Tells the client's screen which station this speaker is on, once its data has arrived. */
    private void publishStationToClient() {
        ClientStations.getInstance().setSpeakerStation(receiver(), tunedTo != null ? tunedTo : ownStation.id());
        ClientStations.getInstance().put(ownStation.view());
    }

    private static UUID readUuid(ValueInput input, String key) {
        int[] bits = input.getIntArray(key).orElse(null);
        return bits != null && bits.length == 4 ? UUIDUtil.uuidFromIntArray(bits) : null;
    }
}
