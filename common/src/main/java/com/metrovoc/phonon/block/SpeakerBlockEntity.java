package com.metrovoc.phonon.block;

import com.metrovoc.phonon.audio.PlaybackState;
import com.google.gson.JsonParser;
import com.metrovoc.phonon.client.ClientStations;
import com.metrovoc.phonon.station.Station;
import com.metrovoc.phonon.station.StationManager;
import com.metrovoc.phonon.server.SpeakerControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Speaker block entity, 存储播放状态和音量。
 * NBT 保存时"坍缩" - 只保存当前位置和 speed，不保存 anchorTime。
 */
public class SpeakerBlockEntity extends BlockEntity {
    private static final float DEFAULT_VOLUME = 0.5f;

    private static Supplier<BlockEntityType<SpeakerBlockEntity>> typeSupplier;
    private PlaybackState playback = PlaybackState.STOPPED;
    private float volume = DEFAULT_VOLUME;
    private Station ownStation = Station.ownedBySpeaker();
    private UUID tunedTo;
    private UUID ownerId;
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
        if (level.isClientSide) {
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

    /** The station this speaker is tuned to, or its own private one. */
    public Station station() {
        Station tuned = StationManager.getInstance().get(tunedTo);
        return tuned != null ? tuned : ownStation;
    }

    public void setOwner(UUID ownerId) {
        this.ownerId = ownerId;
        setChanged();
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    /** Listens to a shared station, or with null goes back to this speaker's own. */
    public void tune(UUID stationId) {
        Station before = station();
        StationManager manager = StationManager.getInstance();
        manager.detach(before.id(), level.dimension(), worldPosition);
        tunedTo = manager.get(stationId) != null ? stationId : null;
        if (tunedTo != null) {
            ownStation.stop();
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
        if (level == null || level.isClientSide) {
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

    public float getVolume() {
        return volume;
    }

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
            if (!level.isClientSide) {
                StationManager manager = StationManager.getInstance();
                manager.addPrivate(ownStation);
                Station station = station();
                manager.attach(station.id(), level.dimension(), worldPosition);
                playback = station.playback();
            } else {
                // 客户端：区块加载时立即恢复播放
                com.metrovoc.phonon.client.ClientSpeakerManager.getInstance().updateSpeaker(worldPosition, playback, volume);
                publishStationToClient();
            }
        }
    }

    @Override
    public void setRemoved() {
        // BlockEntity 被移除时清理状态
        if (level != null) {
            if (level.isClientSide) {
                com.metrovoc.phonon.client.ClientSpeakerManager.getInstance().removeSpeaker(worldPosition);
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
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, provider);
        return tag;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);

        tag.putFloat("volume", volume);
        tag.putInt("poweredSides", poweredSides);
        tag.putString("ownStation", ownStation.toJson().toString());
        if (tunedTo != null) {
            tag.putUUID("tunedTo", tunedTo);
        }
        if (ownerId != null) {
            tag.putUUID("ownerId", ownerId);
        }

        // 保存时"坍缩": 计算当前位置，不保存 anchorTime
        if (playback.resourceId() != null) {
            tag.putUUID("resourceId", playback.resourceId());
            long now = PlaybackState.nowMs();
            long effectivePos = playback.getCurrentPositionMs(now);
            tag.putLong("position", effectivePos);
            tag.putFloat("speed", playback.speed());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);

        volume = sanitizeVolume(tag.contains("volume") ? tag.getFloat("volume") : DEFAULT_VOLUME);
        poweredSides = tag.getInt("poweredSides");
        tunedTo = tag.hasUUID("tunedTo") ? tag.getUUID("tunedTo") : null;
        ownerId = tag.hasUUID("ownerId") ? tag.getUUID("ownerId") : null;
        Station before = ownStation;
        boolean hasStation = false;
        try {
            if (tag.contains("ownStation")) {
                ownStation = Station.fromJson(JsonParser.parseString(tag.getString("ownStation")).getAsJsonObject());
                hasStation = true;
            }
        } catch (RuntimeException e) {
            com.metrovoc.phonon.Phonon.LOGGER.warn("Ignoring a speaker's unreadable station data", e);
        }

        try {
            // 兼容旧格式: playing + startTime
            if (tag.contains("playing")) {
                loadLegacyFormat(tag);
                if (playback.resourceId() != null) {
                    ownStation = Station.migrated(playback.resourceId(), playback.positionAtAnchorMs(), playback.speed());
                }
                relinkAfterLoad(before);
                return;
            }

            // 新格式: resourceId + position + speed
            if (!tag.contains("resourceId")) {
                playback = PlaybackState.STOPPED;
                relinkAfterLoad(before);
                return;
            }

            UUID resourceId = tag.getUUID("resourceId");
            if (resourceId == null) {
                playback = PlaybackState.STOPPED;
                relinkAfterLoad(before);
                return;
            }

            long savedPos = tag.getLong("position");
            float speed = tag.contains("speed") ? tag.getFloat("speed") : 1.0f;

            // 读取时用当前时间作为新的 anchor
            long now = PlaybackState.nowMs();
            playback = new PlaybackState(resourceId, now, savedPos, speed);
            if (!hasStation) {
                ownStation = Station.migrated(resourceId, savedPos, speed);
            }
            relinkAfterLoad(before);

            // 如果此时 level 已存在且为客户端，立即同步
            if (level != null && level.isClientSide) {
                com.metrovoc.phonon.client.ClientSpeakerManager.getInstance().updateSpeaker(worldPosition, playback, volume);
            }
        } catch (Exception e) {
            playback = PlaybackState.STOPPED;
        }
    }

    /**
     * 兼容旧 NBT 格式 (playing + startTime)。
     */
    private void loadLegacyFormat(CompoundTag tag) {
        if (!tag.getBoolean("playing")) {
            playback = PlaybackState.STOPPED;
            return;
        }

        if (!tag.contains("resourceId")) {
            playback = PlaybackState.STOPPED;
            return;
        }

        UUID resourceId = tag.getUUID("resourceId");
        if (resourceId == null) {
            playback = PlaybackState.STOPPED;
            return;
        }

        long startTime = tag.getLong("startTime");
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
        if (level.isClientSide) {
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
        ClientStations.getInstance().setSpeakerStation(worldPosition, tunedTo != null ? tunedTo : ownStation.id());
        ClientStations.getInstance().put(ownStation.view());
    }
}
