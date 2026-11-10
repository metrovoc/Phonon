package com.metrovoc.phonon.client.audio;

/** Decides when written audio has left the range a healthy stream stays in, relative to the shared clock. */
public final class DriftCheck {
    /** The engine queues 4 one-second buffers, so a healthy stream leads the clock by under about five seconds. */
    private static final long MAX_LEAD_MS = 6000;
    /** Behind the clock means the queue ran dry: the listener is hearing the past. */
    private static final long MAX_BEHIND_MS = 400;
    private static final int SETTLE_TICKS = 60;

    private DriftCheck() {}

    public static boolean needsResync(long writtenMs, long clockMs, long durationMs, int ticksSinceStart) {
        if (ticksSinceStart < SETTLE_TICKS) {
            return false;
        }
        if (durationMs > 0 && writtenMs >= durationMs - 1000) {
            return false;
        }
        long lead = writtenMs - clockMs;
        return lead < -MAX_BEHIND_MS || lead > MAX_LEAD_MS;
    }
}
