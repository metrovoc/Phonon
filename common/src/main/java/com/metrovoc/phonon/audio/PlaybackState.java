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
    private static final float MAX_SPEED = 16.0f;
    public static final PlaybackState STOPPED = new PlaybackState(null, 0, 0, 0f);

    public PlaybackState {
        positionAtAnchorMs = Math.max(0, positionAtAnchorMs);
        if (!Float.isFinite(speed) || speed < 0.01f) {
            speed = 0.0f;
        } else {
            speed = Math.min(MAX_SPEED, speed);
        }
    }

    /** Monotonic playback clock, unaffected by wall-clock corrections. */
    public static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }

    public long getCurrentPositionMs() {
        return getCurrentPositionMs(nowMs());
    }

    /**
     * 根据当前时间计算播放位置。
     */
    public long getCurrentPositionMs(long currentTimeMs) {
        if (resourceId == null) return 0;
        long elapsed = currentTimeMs - anchorTimeMs;
        double position = positionAtAnchorMs + (double) elapsed * speed;
        if (position <= 0) return 0;
        if (position >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return (long) position;
    }

    /**
     * 是否正在播放 (有资源且 speed > 0)。
     */
    public boolean isPlaying() {
        return resourceId != null && speed > 0.0f;
    }

    /**
     * 是否暂停 (有资源但 speed = 0)。
     */
    public boolean isPaused() {
        return resourceId != null && speed == 0.0f;
    }

    /**
     * 是否停止 (无资源)。
     */
    public boolean isStopped() {
        return resourceId == null;
    }
}
