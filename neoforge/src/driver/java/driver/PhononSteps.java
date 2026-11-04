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
        Driver.STEPS.put("pdrift", (mc, arg) -> {
            String[] a = arg.trim().split("\\s+");
            BlockPos pos = new BlockPos(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
            var f = com.metrovoc.phonon.client.audio.AudioPlayer.class.getDeclaredField("playingSounds");
            f.setAccessible(true);
            var sound = (com.metrovoc.phonon.client.audio.SpeakerSoundInstance) ((java.util.Map<?, ?>) f.get(com.metrovoc.phonon.client.audio.AudioPlayer.getInstance())).get(pos);
            long clock = ClientSpeakerManager.getInstance().getSpeakerState(pos).orElse(PlaybackState.STOPPED).getCurrentPositionMs();
            long written = sound == null || sound.tap() == null ? -1 : sound.tap().writtenMs();
            LOG.info("[check] {} clockMs={} writtenMs={} leadMs={} paused={}", a[0], clock, written, written - clock, mc.isPaused());
        });
        Driver.STEPS.put("pskew", (mc, arg) -> {
            var f = com.metrovoc.phonon.client.audio.AudioPlayer.class.getDeclaredField("playingSounds");
            f.setAccessible(true);
            String[] a = arg.trim().split("\\s+");
            BlockPos pos = new BlockPos(Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]));
            var sound = (com.metrovoc.phonon.client.audio.SpeakerSoundInstance) ((java.util.Map<?, ?>) f.get(com.metrovoc.phonon.client.audio.AudioPlayer.getInstance())).get(pos);
            var written = com.metrovoc.phonon.client.audio.StreamTap.class.getDeclaredField("samplesWritten");
            written.setAccessible(true);
            var tap = sound.tap();
            written.setLong(tap, written.getLong(tap) - Long.parseLong(a[3]));
            LOG.info("[check] skewed writtenMs={}", tap.writtenMs());
        });
        Driver.STEPS.put("pmuffle", (mc, arg) -> {
            var f = com.metrovoc.phonon.client.audio.AudioPlayer.class.getDeclaredField("playingSounds");
            f.setAccessible(true);
            String[] a = arg.trim().split("\\s+");
            BlockPos pos = new BlockPos(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
            var sound = (com.metrovoc.phonon.client.audio.SpeakerSoundInstance) ((java.util.Map<?, ?>) f.get(com.metrovoc.phonon.client.audio.AudioPlayer.getInstance())).get(pos);
            LOG.info("[check] {} gainHf={} audible={} alError={}", a[0], sound == null ? -1 : sound.muffleGainHf(), sound == null ? -1 : sound.audibleVolume(), org.lwjgl.openal.AL10.alGetError());
        });
        Driver.STEPS.put("pworld", (mc, arg) -> {
            String[] a = arg.trim().split("\\s+");
            var level = mc.getSingleplayerServer().overworld();
            BlockPos comparator = new BlockPos(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
            int signal = level.getBlockState(comparator).getSignal(level, comparator, net.minecraft.core.Direction.WEST);
            var box = new net.minecraft.world.phys.AABB(comparator).inflate(8);
            String parrots = mc.level.getEntitiesOfClass(net.minecraft.world.entity.animal.Parrot.class, box).stream().map(p -> String.valueOf(p.isPartyParrot())).toList().toString();
            String allays = mc.level.getEntitiesOfClass(net.minecraft.world.entity.animal.allay.Allay.class, box).stream().map(p -> String.valueOf(p.isDancing())).toList().toString();
            String sculk = level.getEntitiesOfClass(net.minecraft.world.entity.animal.allay.Allay.class, box).stream().map(p -> String.valueOf(p.isDancing())).toList().toString();
            LOG.info("[check] {} comparator={} parrots={} allays={} serverAllays={}", a[0], signal, parrots, allays, sculk);
        });
        Driver.STEPS.put("ptrack", (mc, arg) -> {
            String[] a = arg.trim().split("\\s+");
            BlockPos pos = new BlockPos(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
            PlaybackState s = ClientSpeakerManager.getInstance().getSpeakerState(pos).orElse(PlaybackState.STOPPED);
            String name = s.resourceId() == null ? "-" : com.metrovoc.phonon.client.ClientAudioManager.getInstance().getResource(s.resourceId()).map(r -> r.name()).orElse("?");
            LOG.info("[check] {} track={} playing={} paused={} posMs={}", a[0], name, s.isPlaying(), s.isPaused(), s.getCurrentPositionMs());
        });
        Driver.STEPS.put("pcomp", (mc, arg) -> {
            String[] a = arg.trim().split("\\s+");
            var server = mc.getSingleplayerServer();
            var level = server.overworld();
            BlockPos speakerPos = new BlockPos(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
            BlockPos comparator = speakerPos.east();
            String line = server.submit(() -> "analog=" + level.getBlockState(speakerPos).getAnalogOutputSignal(level, speakerPos)
                + " comparatorOut=" + level.getSignal(comparator, net.minecraft.core.Direction.WEST)
                + " sensor=" + level.getBlockState(speakerPos.above().south(2)).getValue(net.minecraft.world.level.block.SculkSensorBlock.PHASE)).join();
            LOG.info("[check] {} {}", a[0], line);
        });
        Driver.STEPS.put("reload", (mc, arg) -> mc.reloadResourcePacks());
    }

    public static void init() {
    }
}
