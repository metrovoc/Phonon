package com.metrovoc.phonon.audio;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 锚点模型播放状态。
 * anchorTimeMs: 状态改变的时刻
 * positionAtAnchorMs: 那一刻的播放位置
 * speed: 0.0=暂停, 1.0=播放
 */
public record PlaybackState(
    @Nullable UUID resourceId,
    long anchorTimeMs,
    long positionAtAnchorMs,
    float speed
) {
    public static final PlaybackState STOPPED = new PlaybackState(null, 0, 0, 0f);

    /**
     * 根据当前时间计算播放位置。
     */
    public long getCurrentPositionMs(long currentTimeMs) {
        if (resourceId == null) return 0;
        long elapsed = currentTimeMs - anchorTimeMs;
        return Math.max(0, positionAtAnchorMs + (long)(elapsed * speed));
    }

    /**
     * 是否正在播放 (有资源且 speed > 0)。
     */
    public boolean isPlaying() {
        return resourceId != null && speed > 0.01f;
    }

    /**
     * 是否暂停 (有资源但 speed = 0)。
     */
    public boolean isPaused() {
        return resourceId != null && speed < 0.01f;
    }

    /**
     * 是否停止 (无资源)。
     */
    public boolean isStopped() {
        return resourceId == null;
    }
}
