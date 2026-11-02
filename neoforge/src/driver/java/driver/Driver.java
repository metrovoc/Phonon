package driver;

import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/** Small test driver for targets whose input APIs predate the full driver. */
public final class Driver {
    interface Step { void run(Minecraft mc, String arg) throws Exception; }
    public static final Map<String, Step> STEPS = new HashMap<>();
    private static final Logger LOG = LogUtils.getLogger();
    private static List<String> steps; private static int next, waiting; private static boolean moved, loading, quitting, hover; private static int failed;

    private Driver() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        String script = System.getProperty("driver.script", "");
        if (script.isEmpty()) return;
        if (!moved) { moved = true; moveWindow(mc, System.getProperty("driver.monitor", "")); }
        if (mc.getOverlay() != null) return;
        if (steps == null) {
            try { steps = Files.readAllLines(Path.of(script)); } catch (Exception e) { LOG.error("[driver] script", e); steps = List.of(); }
            mc.options.pauseOnLostFocus = false; mc.options.onboardAccessibility = false;
            mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.01); mc.options.save();
        }
        if (quitting) { if (mc.level == null) mc.stop(); return; }
        if (loading) { if (mc.level == null || mc.player == null || mc.screen != null) return; loading = false; }
        if (hover && mc.player != null && !mc.player.getAbilities().flying) { mc.player.getAbilities().flying = true; mc.player.onUpdateAbilities(); }
        if (waiting > 0) { waiting--; return; }
        while (next < steps.size() && waiting == 0 && !loading && !quitting) {
            String step = steps.get(next++).strip();
            if (step.isEmpty() || step.startsWith("#")) continue;
            LOG.info("[driver] {}", step);
            try { run(mc, step); } catch (Exception e) { failed++; LOG.error("[driver] step failed: {}", step, e); }
        }
    }

    private static void run(Minecraft mc, String step) throws Exception {
        String[] parts = step.split("\\s+", 2); String arg = parts.length > 1 ? parts[1] : ""; String[] a = arg.split("\\s+");
        switch (parts[0]) {
            case "world" -> createWorld(mc, arg);
            case "wait" -> waiting = Integer.parseInt(arg);
            case "cmd" -> mc.player.connection.sendCommand(arg);
            case "gui" -> { mc.options.guiScale().set(Integer.parseInt(arg)); mc.resizeDisplay(); }
            case "fly" -> hover = arg.equals("on");
            case "use" -> { BlockPos p = new BlockPos(Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]));
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(p).relative(Direction.UP, 0.5), Direction.UP, p, false)); }
            case "click" -> { double x = Double.parseDouble(a[0]), y = Double.parseDouble(a[1]);
                if (mc.screen instanceof AbstractContainerScreen<?> s) { x += s.getGuiLeft(); y += s.getGuiTop(); } click(mc, x, y); }
            case "press" -> { var w = mc.screen.children().stream().filter(c -> c instanceof AbstractWidget v && v.getMessage().getString().contains(arg)).map(c -> (AbstractWidget) c).findFirst().orElseThrow();
                click(mc, w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0); }
            case "key" -> mc.screen.onClose();
            case "type" -> { for (char c : arg.toCharArray()) charTyped(mc, c); }
            case "typespace" -> charTyped(mc, ' ');
            case "inspect" -> mc.screen.children().forEach(c -> { if (c instanceof AbstractWidget w) LOG.info("[driver]   {} \"{}\" at {},{}", w.getClass().getSimpleName(), w.getMessage().getString(), w.getX(), w.getY()); });
            case "shot" -> shot(mc, arg);
            case "join" -> { ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(arg), new ServerData("test", arg, ServerData.Type.OTHER), false, null); loading = true; }
            case "leave" -> { hover = false; disconnect(mc); }
            case "quit" -> { LOG.info("[driver] done: {} step(s) failed", failed); quitting = true; if (mc.level != null) disconnect(mc); }
            default -> { Step s = STEPS.get(parts[0]); if (s == null) throw new IllegalArgumentException("unknown step " + parts[0]); s.run(mc, arg); }
        }
    }

    private static void createWorld(Minecraft mc, String seed) throws Exception {
        LevelStorageSource source = mc.getLevelSource();
        if (source.levelExists("Driver")) { try (LevelStorageSource.LevelStorageAccess access = source.createAccess("Driver")) { access.deleteLevel(); } }
        LevelSettings settings = new LevelSettings("Driver", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("Driver", settings, new WorldOptions(Long.parseLong(seed), true, false), WorldPresets::createNormalWorldDimensions, mc.screen);
        loading = true;
    }

    private static void moveWindow(Minecraft mc, String name) {
        if (name.isEmpty()) return;
        PointerBuffer monitors = GLFW.glfwGetMonitors();
        for (int i = 0; monitors != null && i < monitors.limit(); i++) {
            long m = monitors.get(i);
            if (name.equals(GLFW.glfwGetMonitorName(m))) { int[] x = new int[1], y = new int[1]; GLFW.glfwGetMonitorPos(m, x, y); GLFW.glfwSetWindowPos(HANDLE(mc), x[0] + 32, y[0] + 64); return; }
        }
    }
    
    private static void click(Minecraft mc, double x, double y) { mc.screen.mouseClicked(x, y, 0); if (mc.screen != null) mc.screen.mouseReleased(x, y, 0); }
    private static void charTyped(Minecraft mc, char c) { mc.screen.charTyped(c, 0); }
    private static void shot(Minecraft mc, String n) { Screenshot.grab(mc.gameDirectory, n + ".png", mc.getMainRenderTarget(), m -> LOG.info("[driver] {}", m.getString())); }
    private static void disconnect(Minecraft mc) { mc.level.disconnect(); mc.disconnect(); }
    private static long HANDLE(Minecraft mc) { return mc.getWindow().getWindow(); }

}
