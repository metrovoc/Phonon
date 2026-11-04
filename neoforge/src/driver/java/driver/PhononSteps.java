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
            PlaybackState s = ClientSpeakerManager.getInstance().getSpeakerState(new com.metrovoc.phonon.audio.Receiver.Block(pos)).orElse(PlaybackState.STOPPED);
            Object sound = null;
            try {
                var f = com.metrovoc.phonon.client.audio.AudioPlayer.class.getDeclaredField("playingSounds");
                f.setAccessible(true);
                sound = ((java.util.Map<?, ?>) f.get(com.metrovoc.phonon.client.audio.AudioPlayer.getInstance())).get(new com.metrovoc.phonon.audio.Receiver.Block(pos));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            boolean active = sound != null && mc.getSoundManager().isActive((net.minecraft.client.resources.sounds.SoundInstance) sound);
            LOG.info("[check] {} playing={} paused={} stopped={} posMs={} vol={} soundActive={}", a[0], s.isPlaying(), s.isPaused(),
                s.isStopped(), s.getCurrentPositionMs(), ClientSpeakerManager.getInstance().getSpeakerVolume(new com.metrovoc.phonon.audio.Receiver.Block(pos)), active);
        });
        Driver.STEPS.put("config", (mc, arg) -> mc.setScreen(new com.metrovoc.phonon.config.PhononConfigScreen(null)));
        Driver.STEPS.put("pdrift", (mc, arg) -> {
            String[] a = arg.trim().split("\\s+");
            BlockPos pos = new BlockPos(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
            var f = com.metrovoc.phonon.client.audio.AudioPlayer.class.getDeclaredField("playingSounds");
            f.setAccessible(true);
            var sound = (com.metrovoc.phonon.client.audio.SpeakerSoundInstance) ((java.util.Map<?, ?>) f.get(com.metrovoc.phonon.client.audio.AudioPlayer.getInstance())).get(new com.metrovoc.phonon.audio.Receiver.Block(pos));
            long clock = ClientSpeakerManager.getInstance().getSpeakerState(new com.metrovoc.phonon.audio.Receiver.Block(pos)).orElse(PlaybackState.STOPPED).getCurrentPositionMs();
            long written = sound == null || sound.tap() == null ? -1 : sound.tap().writtenMs();
            LOG.info("[check] {} clockMs={} writtenMs={} leadMs={} paused={}", a[0], clock, written, written - clock, mc.isPaused());
        });
        Driver.STEPS.put("pskew", (mc, arg) -> {
            var f = com.metrovoc.phonon.client.audio.AudioPlayer.class.getDeclaredField("playingSounds");
            f.setAccessible(true);
            String[] a = arg.trim().split("\\s+");
            BlockPos pos = new BlockPos(Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]));
            var sound = (com.metrovoc.phonon.client.audio.SpeakerSoundInstance) ((java.util.Map<?, ?>) f.get(com.metrovoc.phonon.client.audio.AudioPlayer.getInstance())).get(new com.metrovoc.phonon.audio.Receiver.Block(pos));
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
            var sound = (com.metrovoc.phonon.client.audio.SpeakerSoundInstance) ((java.util.Map<?, ?>) f.get(com.metrovoc.phonon.client.audio.AudioPlayer.getInstance())).get(new com.metrovoc.phonon.audio.Receiver.Block(pos));
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
            PlaybackState s = ClientSpeakerManager.getInstance().getSpeakerState(new com.metrovoc.phonon.audio.Receiver.Block(pos)).orElse(PlaybackState.STOPPED);
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
        Driver.STEPS.put("pqueue", (mc, arg) -> {
            String[] a = arg.trim().split("\\s+");
            BlockPos pos = new BlockPos(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
            var view = com.metrovoc.phonon.client.ClientStations.getInstance().stationOf(new com.metrovoc.phonon.audio.Receiver.Block(pos));
            PlaybackState s = ClientSpeakerManager.getInstance().getSpeakerState(new com.metrovoc.phonon.audio.Receiver.Block(pos)).orElse(PlaybackState.STOPPED);
            var names = com.metrovoc.phonon.client.ClientAudioManager.getInstance();
            String now = s.resourceId() == null ? "-" : names.getResource(s.resourceId()).map(r -> r.name()).orElse("?");
            LOG.info("[check] {} now={} playing={} posMs={} station={} shared={} queue={} cursor={} repeat={} shuffle={}", a[0], now, s.isPlaying(), s.getCurrentPositionMs(),
                view == null ? "-" : view.name(), view != null && view.shared(), view == null ? 0 : view.queue().size(), view == null ? -1 : view.cursor(),
                view == null ? "-" : view.repeat(), view != null && view.shuffle());
        });
        Driver.STEPS.put("pboom", (mc, arg) -> {
            var manager = ClientSpeakerManager.getInstance();
            var player = com.metrovoc.phonon.client.audio.AudioPlayer.getInstance();
            try {
                var f = ClientSpeakerManager.class.getDeclaredField("boomboxes");
                f.setAccessible(true);
                var sounds = com.metrovoc.phonon.client.audio.AudioPlayer.class.getDeclaredField("playingSounds");
                sounds.setAccessible(true);
                var heard = ((java.util.Map<?, ?>) f.get(manager)).keySet();
                if (heard.isEmpty()) {
                    LOG.info("[check] {} none", arg.trim());
                }
                for (Object id : heard) {
                    var r = new com.metrovoc.phonon.audio.Receiver.Boombox((java.util.UUID) id);
                    PlaybackState s = manager.getSpeakerState(r).orElse(PlaybackState.STOPPED);
                    var sound = (com.metrovoc.phonon.client.audio.SpeakerSoundInstance) ((java.util.Map<?, ?>) sounds.get(player)).get(r);
                    var at = manager.sourcePosition(r);
                    var view = com.metrovoc.phonon.client.ClientStations.getInstance().stationOf(r);
                    LOG.info("[check] {} wall={} posMs={} playing={} paused={} soundActive={} sound={} written={} src={},{},{} carriedHere={} dist={} vol={} station={} shared={} queue={}",
                        arg.trim(), System.currentTimeMillis(), s.getCurrentPositionMs(), s.isPlaying(), s.isPaused(),
                        sound != null && mc.getSoundManager().isActive(sound), sound == null ? 0 : System.identityHashCode(sound),
                        sound == null || sound.tap() == null ? -1 : sound.tap().writtenMs(),
                        String.format(java.util.Locale.ROOT, "%.1f", at.x), String.format(java.util.Locale.ROOT, "%.1f", at.y), String.format(java.util.Locale.ROOT, "%.1f", at.z),
                        manager.carriedHere(r), String.format(java.util.Locale.ROOT, "%.1f", mc.player.getEyePosition().distanceTo(at)), manager.getSpeakerVolume(r),
                        view == null ? "-" : view.name(), view != null && view.shared(), view == null ? 0 : view.queue().size());
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
        Driver.STEPS.put("useitem", (mc, arg) -> mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND));
        Driver.STEPS.put("slot", (mc, arg) -> mc.player.getInventory().selected = Integer.parseInt(arg.trim()));
        Driver.STEPS.put("psearch", (mc, arg) -> {
            var search = com.metrovoc.phonon.client.gui.ClientSearch.getInstance();
            try {
                var results = (java.util.List<?>) field(search, "results");
                var pictures = (java.util.Map<?, ?>) field(search, "pictures");
                LOG.info("[check] {} searching={} failure={} results={} pictures={} query={} first={}", arg.trim(), field(search, "searching"), field(search, "failureKey"),
                    results.size(), pictures.size(), field(search, "query"), results.isEmpty() ? "-" : results.get(0));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
        Driver.STEPS.put("pdrop", (mc, arg) -> mc.player.drop(false));
        Driver.STEPS.put("psneak", (mc, arg) -> mc.options.keyShift.setDown(arg.trim().equals("on")));
        Driver.STEPS.put("pclick", (mc, arg) -> mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, Integer.parseInt(arg.trim()), 0,
            net.minecraft.world.inventory.ClickType.PICKUP, mc.player));
        Driver.STEPS.put("plists", (mc, arg) -> {
            var client = com.metrovoc.phonon.client.ClientStations.getInstance();
            LOG.info("[check] {} stations={} playlists={} jobs={}", arg.trim(),
                client.shared().stream().map(v -> v.name() + "/" + v.ownerName()).toList(),
                client.playlists().stream().map(p -> p.name() + "/" + p.ownerName() + "/" + p.tracks().size()).toList(), client.jobs());
        });
        Driver.STEPS.put("plegacy", (mc, arg) -> {
            String[] a = arg.trim().split("\\s+");
            var track = com.metrovoc.phonon.client.ClientAudioManager.getInstance().getAllResources().get(0).id();
            int[] id = net.minecraft.core.UUIDUtil.uuidToIntArray(track);
            mc.player.connection.sendCommand("setblock " + a[0] + " " + a[1] + " " + a[2] + " phonon:speaker[facing=west]{resourceId:[I;"
                + id[0] + "," + id[1] + "," + id[2] + "," + id[3] + "],position:5000L,speed:1.0f,volume:0.5f}");
        });
        Driver.STEPS.put("reload", (mc, arg) -> mc.reloadResourcePacks());
    }

    public static void init() {
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
}
