package com.metrovoc.phonon.block;

import com.metrovoc.phonon.audio.PlaybackState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

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
        this.playback = playback;
        setChanged();

        // 同步 PLAYING blockstate 用于视觉效果 (灯光 + 粒子)
        if (level != null) {
            BlockState state = getBlockState();
            boolean shouldPlay = playback.isPlaying();
            if (state.getValue(SpeakerBlock.PLAYING) != shouldPlay) {
                level.setBlock(worldPosition, state.setValue(SpeakerBlock.PLAYING, shouldPlay), 3);
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
                // 服务端加载时，如果有活跃播放状态，注册到 ServerSpeakerManager
                if (playback.isPlaying() || playback.isPaused()) {
                    com.metrovoc.phonon.server.ServerSpeakerManager.getInstance().registerSpeaker(
                        level.dimension(),
                        worldPosition,
                        playback,
                        com.metrovoc.phonon.server.ServerSpeakerManager.getDurationMs(playback.resourceId())
                    );
                }
            } else {
                // 客户端：区块加载时立即恢复播放
                com.metrovoc.phonon.client.ClientSpeakerManager.getInstance().updateSpeaker(worldPosition, playback, volume);
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
                com.metrovoc.phonon.server.ServerSpeakerManager.getInstance()
                    .unregisterSpeaker(level.dimension(), worldPosition);
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

        // 保存时"坍缩": 计算当前位置，不保存 anchorTime
        if (playback.resourceId() != null) {
            tag.putIntArray("resourceId", UUIDUtil.uuidToIntArray(playback.resourceId()));
            long now = PlaybackState.nowMs();
            long effectivePos = playback.getCurrentPositionMs(now);
            tag.putLong("position", effectivePos);
            tag.putFloat("speed", playback.speed());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);

        volume = sanitizeVolume(tag.getFloatOr("volume", DEFAULT_VOLUME));

        try {
            // 兼容旧格式: playing + startTime
            if (tag.contains("playing")) {
                loadLegacyFormat(tag);
                return;
            }

            // 新格式: resourceId + position + speed
            if (!tag.contains("resourceId")) {
                playback = PlaybackState.STOPPED;
                return;
            }

            UUID resourceId = readUuid(tag, "resourceId");
            if (resourceId == null) {
                playback = PlaybackState.STOPPED;
                return;
            }

            long savedPos = tag.getLongOr("position", 0L);
            float speed = tag.getFloatOr("speed", 1.0f);

            // 读取时用当前时间作为新的 anchor
            long now = PlaybackState.nowMs();
            playback = new PlaybackState(resourceId, now, savedPos, speed);

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
        if (!tag.getBooleanOr("playing", false)) {
            playback = PlaybackState.STOPPED;
            return;
        }

        if (!tag.contains("resourceId")) {
            playback = PlaybackState.STOPPED;
            return;
        }

        UUID resourceId = readUuid(tag, "resourceId");
        if (resourceId == null) {
            playback = PlaybackState.STOPPED;
            return;
        }

        long startTime = tag.getLongOr("startTime", System.currentTimeMillis());
        long elapsed = System.currentTimeMillis() - startTime;
        long now = PlaybackState.nowMs();

        // 转换为新格式: anchor=now, position=elapsed, speed=1.0
        playback = new PlaybackState(resourceId, now, Math.max(0, elapsed), 1.0f);
    }

    private static UUID readUuid(CompoundTag tag, String key) {
        int[] bits = tag.getIntArray(key).orElse(null);
        return bits != null && bits.length == 4 ? UUIDUtil.uuidFromIntArray(bits) : null;
    }
}
