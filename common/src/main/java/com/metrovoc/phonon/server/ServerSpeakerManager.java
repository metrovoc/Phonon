package com.metrovoc.phonon.server;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.audio.AudioManager;
import com.metrovoc.phonon.audio.AudioResource;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.block.SpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端 Speaker 管理器。
 * 追踪所有活跃 speaker，定期检查播放结束。
 */
public class ServerSpeakerManager {
    private static ServerSpeakerManager instance;

    private final Map<SpeakerKey, SpeakerInfo> activeSpeakers = new ConcurrentHashMap<>();

    /**
     * 停止回调，由平台层设置，用于发送网络包。
     */
    private StopCallback stopCallback;

    @FunctionalInterface
    public interface StopCallback {
        void onSpeakerStopped(ServerLevel level, BlockPos pos, SpeakerBlockEntity speaker);
    }

    private ServerSpeakerManager() {}

    public static ServerSpeakerManager getInstance() {
        if (instance == null) {
            instance = new ServerSpeakerManager();
        }
        return instance;
    }

    public static void reset() {
        instance = null;
    }

    public void setStopCallback(StopCallback callback) {
        this.stopCallback = callback;
    }

    /**
     * 注册活跃 speaker。
     */
    public void registerSpeaker(ResourceKey<Level> dimension, BlockPos pos, PlaybackState state, long durationMs) {
        if (!state.isPlaying() && !state.isPaused()) {
            activeSpeakers.remove(new SpeakerKey(dimension, pos));
            return;
        }
        activeSpeakers.put(new SpeakerKey(dimension, pos), new SpeakerInfo(state, durationMs));
    }

    /**
     * 注销 speaker。
     */
    public void unregisterSpeaker(ResourceKey<Level> dimension, BlockPos pos) {
        activeSpeakers.remove(new SpeakerKey(dimension, pos));
    }

    /**
     * 每秒 tick，检查播放是否结束。
     */
    public void tick(MinecraftServer server, long tickCount) {
        // 每 20 tick (1 秒) 检查一次
        if (tickCount % 20 != 0) return;

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<SpeakerKey, SpeakerInfo>> it = activeSpeakers.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<SpeakerKey, SpeakerInfo> entry = it.next();
            SpeakerKey key = entry.getKey();
            SpeakerInfo info = entry.getValue();

            // 暂停状态不自动结束
            if (info.state.isPaused()) continue;

            // 未知 duration 的不自动结束
            if (info.durationMs <= 0) continue;

            long currentPos = info.state.getCurrentPositionMs(now);
            if (currentPos >= info.durationMs) {
                // 播放结束
                it.remove();
                stopSpeaker(server, key);
            }
        }
    }

    private void stopSpeaker(MinecraftServer server, SpeakerKey key) {
        ServerLevel level = server.getLevel(key.dimension);
        if (level == null) return;

        if (!(level.getBlockEntity(key.pos) instanceof SpeakerBlockEntity speaker)) return;

        speaker.setPlayback(PlaybackState.STOPPED);

        // 通过回调发送网络包
        if (stopCallback != null) {
            stopCallback.onSpeakerStopped(level, key.pos, speaker);
        }

        Phonon.LOGGER.debug("Auto-stopped speaker at {} in {}", key.pos, key.dimension.location());
    }

    /**
     * 获取音频 duration (从 AudioManager)。
     */
    public static long getDurationMs(java.util.UUID resourceId) {
        Optional<AudioResource> resource = AudioManager.getInstance().getResource(resourceId);
        return resource.map(AudioResource::durationMs).orElse(-1L);
    }

    /**
     * 获取指定维度中所有活跃 speaker 的状态 (用于新玩家同步)。
     */
    public Map<BlockPos, SpeakerSyncData> getActiveSpeakersInDimension(ResourceKey<Level> dimension) {
        Map<BlockPos, SpeakerSyncData> result = new java.util.HashMap<>();
        long now = System.currentTimeMillis();

        for (var entry : activeSpeakers.entrySet()) {
            if (entry.getKey().dimension.equals(dimension)) {
                result.put(entry.getKey().pos, new SpeakerSyncData(entry.getValue().state, now));
            }
        }
        return result;
    }

    /**
     * 同步数据记录。
     */
    public record SpeakerSyncData(PlaybackState state, long serverTimeMs) {}

    private record SpeakerKey(ResourceKey<Level> dimension, BlockPos pos) {}

    private record SpeakerInfo(PlaybackState state, long durationMs) {}
}
