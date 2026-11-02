package driver;

import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.client.ClientSpeakerManager;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;

/** Checklist steps: report speaker state to the log so a run can be judged from text. */
public final class PhononSteps {
    private static final Logger LOG = LogUtils.getLogger();

    private PhononSteps() {
    }

    static {
        Driver.STEPS.put("pstate", (mc, arg) -> {
            String[] a = arg.trim().split("\\s+");
            BlockPos pos = new BlockPos(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
            PlaybackState s = ClientSpeakerManager.getInstance().getSpeakerState(pos).orElse(PlaybackState.STOPPED);
            Object sound = null;
            try {
                var f = com.metrovoc.phonon.client.audio.AudioPlayer.class.getDeclaredField("playingSounds");
                f.setAccessible(true);
                sound = ((java.util.Map<?, ?>) f.get(com.metrovoc.phonon.client.audio.AudioPlayer.getInstance())).get(pos);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            boolean active = sound != null && mc.getSoundManager().isActive((net.minecraft.client.resources.sounds.SoundInstance) sound);
            LOG.info("[check] {} playing={} paused={} stopped={} posMs={} vol={} soundActive={}", a[0], s.isPlaying(), s.isPaused(),
                s.isStopped(), s.getCurrentPositionMs(), ClientSpeakerManager.getInstance().getSpeakerVolume(pos), active);
        });
        Driver.STEPS.put("config", (mc, arg) -> mc.setScreen(new com.metrovoc.phonon.config.PhononConfigScreen(null)));
        Driver.STEPS.put("reload", (mc, arg) -> mc.reloadResourcePacks());
    }

    public static void init() {
    }
}
