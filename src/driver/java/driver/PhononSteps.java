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
    private static final java.util.List<com.metrovoc.phonon.client.AudioDownloadSession> burst = new java.util.ArrayList<>();
    private record Bookmark(java.util.UUID track, long positionMs) {}
    private static final java.util.Map<String, Bookmark> bookmarks = new java.util.HashMap<>();

    private PhononSteps() {
    }

    static {
        Driver.STEPS.put("pmemory", (mc, arg) -> {
            var heap = java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            long directUsed = 0;
            long directCount = 0;
            for (var pool : java.lang.management.ManagementFactory.getPlatformMXBeans(java.lang.management.BufferPoolMXBean.class)) {
                if (pool.getName().equals("direct")) {
                    directUsed = pool.getMemoryUsed();
                    directCount = pool.getCount();
                }
            }
            long collections = 0;
            long gcTime = 0;
            for (var collector : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
                collections += Math.max(0, collector.getCollectionCount());
                gcTime += Math.max(0, collector.getCollectionTime());
            }
            LOG.info("[memory] {} uptimeMs={} heapUsedBytes={} heapCommittedBytes={} directUsedBytes={} directCount={} gcCollections={} gcTimeMs={}",
                arg.trim(), java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime(), heap.getUsed(), heap.getCommitted(),
                directUsed, directCount, collections, gcTime);
        });
        Driver.STEPS.put("pbookmark", (mc, arg) -> {
            String[] a = arg.trim().split("\\s+");
            var state = ClientSpeakerManager.getInstance().getSpeakerState(receiver(java.util.Arrays.copyOfRange(a, 1, 4)))
                .orElseThrow();
            if (!state.isPlaying()) {
                throw new IllegalStateException("Bookmark requires active playback");
            }
            bookmarks.put(a[0], new Bookmark(state.resourceId(), state.getCurrentPositionMs()));
            LOG.info("[check] bookmark {} position={}ms", a[0], state.getCurrentPositionMs());
        });
        Driver.STEPS.put("passertprogress", (mc, arg) -> {
            String[] a = arg.trim().split("\\s+");
            var before = java.util.Objects.requireNonNull(bookmarks.remove(a[0]), "Missing playback bookmark");
            var state = ClientSpeakerManager.getInstance().getSpeakerState(receiver(java.util.Arrays.copyOfRange(a, 1, 4)))
                .orElseThrow();
            long after = state.getCurrentPositionMs();
            if (!before.track().equals(state.resourceId()) || after < before.positionMs()) {
                throw new IllegalStateException("Playback rewound across unload: before=" + before.positionMs() + "ms after=" + after + "ms");
            }
            LOG.info("[check] ASSERT progress {} before={}ms after={}ms passed", a[0], before.positionMs(), after);
        });
        Driver.STEPS.put("passertlibrary", (mc, arg) -> {
            int expected = Integer.parseInt(arg.trim());
            int count = com.metrovoc.phonon.client.ClientAudioManager.getInstance().getAllResources().size();
            if (count != expected) {
                throw new IllegalStateException("Library expected " + expected + " tracks, got " + count);
            }
            LOG.info("[check] ASSERT library={} passed", count);
        });
        Driver.STEPS.put("passertvolume", (mc, arg) -> {
            String[] a = arg.trim().split("\\s+");
            float actual = ClientSpeakerManager.getInstance().getSpeakerVolume(receiver(a));
            if (Math.abs(actual - Float.parseFloat(a[3])) > 0.001f) {
                throw new IllegalStateException("Volume mismatch: " + arg + "; actual=" + actual);
            }
            LOG.info("[check] ASSERT volume {} passed", arg);
        });
        Driver.STEPS.put("pburst", (mc, arg) -> {
            if (!burst.isEmpty()) {
                throw new IllegalStateException("Previous burst still owns stream leases");
            }
            var resource = com.metrovoc.phonon.client.ClientAudioManager.getInstance().getAllResources().stream()
                .filter(r -> r.name().equals(arg.trim())).findFirst().orElseThrow();
            var manager = com.metrovoc.phonon.client.StreamingAudioManager.getInstance();
            for (int i = 0; i < 18; i++) {
                var acquisition = manager.acquire(resource.id(), i * 3000L);
                if (!acquisition.requestRequired()) {
                    throw new IllegalStateException("Burst must start with no reusable sessions");
                }
                burst.add(acquisition.session());
                com.metrovoc.phonon.platform.PlatformHelper.INSTANCE.requestAudioFromServer(
                    acquisition.session().getStreamId(), resource.id(), i * 3000L);
            }
        });
        Driver.STEPS.put("pburstcheck", (mc, arg) -> {
            if (burst.size() != 18) {
                throw new IllegalStateException("Burst setup did not finish");
            }
            if (arg.trim().equals("fair")) {
                if (burst.getFirst().isDownloadComplete() || !burst.get(1).getBuffer().hasEnoughData(32768)) {
                    throw new IllegalStateException("Second transfer did not start before the first completed");
                }
                LOG.info("[check] ASSERT concurrent transfer progress passed");
            } else {
                try {
                    for (int i = 16; i < 18; i++) {
                        var session = burst.get(i);
                        if (!session.isClosed() || (int) field(session, "retryAttempts") != 3) {
                            throw new IllegalStateException("Capacity rejection did not terminate after three retries: " + i);
                        }
                    }
                    LOG.info("[check] ASSERT bounded capacity retries passed");
                } finally {
                    var manager = com.metrovoc.phonon.client.StreamingAudioManager.getInstance();
                    burst.forEach(s -> manager.release(s.getStreamId()));
                    manager.clear();
                    burst.clear();
                }
            }
        });
        // Protocol-driven soak steps exercise the same server handlers as the screen.
        Driver.STEPS.put("paction", (mc, arg) -> {
            String[] a = arg.trim().split("\\s+", 5);
            var receiver = receiver(a);
            var action = com.metrovoc.phonon.server.SpeakerAction.valueOf(a[3]);
            java.util.UUID track = null;
            String text = "";
            if (a.length == 5) {
                if (action == com.metrovoc.phonon.server.SpeakerAction.CREATE_STATION) {
                    text = a[4];
                } else if (action == com.metrovoc.phonon.server.SpeakerAction.TUNE) {
                    track = com.metrovoc.phonon.client.ClientStations.getInstance().shared().stream()
                        .filter(s -> s.name().equals(a[4])).findFirst().orElseThrow().id();
                } else {
                    track = com.metrovoc.phonon.client.ClientAudioManager.getInstance().getAllResources().stream()
                        .filter(r -> r.name().equals(a[4])).findFirst().orElseThrow().id();
                }
            }
            com.metrovoc.phonon.platform.PlatformHelper.INSTANCE.sendSpeakerAction(receiver, action, track, null, 0, text);
        });
        Driver.STEPS.put("pseek", (mc, arg) -> {
            String[] a = arg.trim().split("\\s+");
            com.metrovoc.phonon.platform.PlatformHelper.INSTANCE.seekSpeaker(receiver(a), Long.parseLong(a[3]));
        });
        Driver.STEPS.put("pvolume", (mc, arg) -> {
            String[] a = arg.trim().split("\\s+");
            com.metrovoc.phonon.platform.PlatformHelper.INSTANCE.setSpeakerVolume(receiver(a), Float.parseFloat(a[3]));
        });
        Driver.STEPS.put("passert", (mc, arg) -> {
            String[] a = arg.trim().split("\\s+", 5);
            var receiver = receiver(a);
            var state = ClientSpeakerManager.getInstance().getSpeakerState(receiver).orElse(PlaybackState.STOPPED);
            boolean matches = switch (a[3]) {
                case "playing" -> state.isPlaying();
                case "paused" -> state.isPaused();
                case "stopped" -> state.isStopped();
                default -> throw new IllegalArgumentException("Expected playing, paused or stopped");
            };
            var sounds = (java.util.Map<?, ?>) field(com.metrovoc.phonon.client.audio.AudioPlayer.getInstance(), "playingSounds");
            var sound = (com.metrovoc.phonon.client.audio.SpeakerSoundInstance) sounds.get(receiver);
            if (!matches || state.isPlaying() && (sound == null || !mc.getSoundManager().isActive(sound))) {
                throw new IllegalStateException("Playback assertion failed: " + arg + "; state=" + state + "; sound=" + sound);
            }
            if (a.length == 5 && !com.metrovoc.phonon.client.ClientAudioManager.getInstance().getResource(state.resourceId())
                .map(r -> r.name().equals(a[4])).orElse(false)) {
                throw new IllegalStateException("Wrong track: " + arg);
            }
            LOG.info("[check] ASSERT {} passed position={}ms", arg, state.getCurrentPositionMs());
        });
        Driver.STEPS.put("ptooltip", (mc, arg) -> {
            var stack = mc.player.getMainHandItem();
            var lines = stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player,
                net.minecraft.world.item.TooltipFlag.NORMAL);
            LOG.info("[check] tooltip {}", lines.stream().map(net.minecraft.network.chat.Component::getString).toList());
        });
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
            LOG.info("[check] {} playing={} paused={} stopped={} posMs={} vol={} soundActive={} wall={}", a[0], s.isPlaying(), s.isPaused(),
                s.isStopped(), s.getCurrentPositionMs(), ClientSpeakerManager.getInstance().getSpeakerVolume(new com.metrovoc.phonon.audio.Receiver.Block(pos)), active,
                System.currentTimeMillis());
        });
        Driver.STEPS.put("config", (mc, arg) -> mc.gui.setScreen(new com.metrovoc.phonon.config.PhononConfigScreen(null)));
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
        Driver.STEPS.put("pgm", (mc, arg) -> {
            // Runs a command as the player with gamemaster permission (level 2) instead of owner
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                var source = server.getPlayerList().getPlayers().getFirst().createCommandSourceStack()
                    //? if >=1.21.11 {
                    .withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER);
                    //?} else
                    /*.withPermission(2);*/
                server.getCommands().performPrefixedCommand(source, arg.trim());
            });
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
            String parrots = mc.level.getEntitiesOfClass(net.minecraft.world.entity.animal.parrot.Parrot.class, box).stream().map(p -> String.valueOf(p.isPartyParrot())).toList().toString();
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
            //? if >=1.21.9 {
            int analog = server.submit(() -> level.getBlockState(speakerPos).getAnalogOutputSignal(level, speakerPos, net.minecraft.core.Direction.EAST)).join();
            //?} else
            /*int analog = server.submit(() -> level.getBlockState(speakerPos).getAnalogOutputSignal(level, speakerPos)).join();*/
            String line = server.submit(() -> "analog=" + analog
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
                    int alRelative = -1;
                    if (sound != null) {
                        var source = com.metrovoc.phonon.client.audio.SpeakerSoundInstance.class.getDeclaredField("channelSource");
                        source.setAccessible(true);
                        int al = source.getInt(sound);
                        alRelative = al == 0 ? -1 : org.lwjgl.openal.AL10.alGetSourcei(al, org.lwjgl.openal.AL10.AL_SOURCE_RELATIVE);
                    }
                    LOG.info("[check] {} atEars={} alRelative={} played={},{},{}", arg.trim(), sound != null && sound.isRelative(), alRelative,
                        sound == null ? 0 : sound.getX(), sound == null ? 0 : sound.getY(), sound == null ? 0 : sound.getZ());
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
        Driver.STEPS.put("psearch", (mc, arg) -> {
            var search = com.metrovoc.phonon.client.gui.ClientSearch.getInstance();
            try {
                var results = (java.util.List<?>) field(search, "results");
                var pictures = (java.util.Map<?, ?>) field(search, "pictures");
                LOG.info("[check] {} searching={} failure={} results={} pictures={} query={} first={}", arg.trim(), field(search, "searching"), field(search, "failure"),
                    results.size(), pictures.size(), field(search, "query"), results.isEmpty() ? "-" : results.get(0));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
        //? if >=26.3 {
        Driver.STEPS.put("pdrop", (mc, arg) -> mc.gameMode.dropItem(mc.player, false));
        //?} else
        /*Driver.STEPS.put("pdrop", (mc, arg) -> mc.player.drop(false));*/
        Driver.STEPS.put("psneak", (mc, arg) -> mc.options.keyShift.setDown(arg.trim().equals("on")));
        Driver.STEPS.put("pclick", (mc, arg) -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, Integer.parseInt(arg.trim()), 0,
            net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player));
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

    private static com.metrovoc.phonon.audio.Receiver.Block receiver(String[] args) {
        return new com.metrovoc.phonon.audio.Receiver.Block(new BlockPos(
            Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2])));
    }
}
