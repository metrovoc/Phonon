package com.metrovoc.phonon.client.gui;

import com.metrovoc.phonon.audio.AudioResource;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.client.ClientAudioManager;
import com.metrovoc.phonon.client.ClientSpeakerManager;
import com.metrovoc.phonon.client.LiveInputManager;
import com.metrovoc.phonon.client.audio.AudioInputDevice;
import com.metrovoc.phonon.menu.SpeakerMenu;
import com.metrovoc.phonon.network.packets.LiveBroadcastChunkPacket;
import com.metrovoc.phonon.network.packets.LiveBroadcastEndPacket;
import com.metrovoc.phonon.network.packets.LiveBroadcastStartPacket;
import com.metrovoc.phonon.network.packets.SpeakerControlPacket;
import com.metrovoc.phonon.network.packets.SpeakerSeekPacket;
import com.metrovoc.phonon.network.packets.SpeakerVolumePacket;
import com.metrovoc.phonon.platform.PlatformHelper;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Speaker control GUI with external tabs.
 *
 * Layout:
 *   [Tabs]  [Main GUI 340x220]
 *   +----+  +------------------------------------------+
 *   | T  |  |  Content Area (varies by tab)           |
 *   +----+  |                                          |
 *   | L  |  |                                          |
 *   +----+  +------------------------------------------+
 */
public class SpeakerScreen extends AbstractContainerScreen<SpeakerMenu> {
    private static final int GUI_WIDTH = 340;
    private static final int GUI_HEIGHT = 220;

    private static final int PADDING = 8;
    private static final int GAP = 8;
    private static final int SEARCH_HEIGHT = 20;
    private static final int TRACK_ITEM_HEIGHT = 18;
    private static final int SLIDER_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 20;
    private static final int PROGRESS_HEIGHT = 24;

    private static final int TAB_WIDTH = 28;
    private static final int TAB_HEIGHT = 28;
    private static final int TAB_GAP = 4;

    private static final int LEFT_COL_WIDTH = 140;

    private static final float DEFAULT_VOLUME = 0.5f;

    private static final String BT_RECEIVER_LINK = "ms-windows-store://pdp/?productid=9N9WCLWDQS5J";
    private static final String VB_CABLE_LINK = "https://vb-audio.com/Cable/";

    private enum Tab { TRACKS, LINE_IN }
    private Tab currentTab = Tab.TRACKS;

    // Widget groups for visibility management
    private final List<AbstractWidget> tracksWidgets = new ArrayList<>();
    private final List<AbstractWidget> lineInWidgets = new ArrayList<>();

    // Tracks tab widgets
    private EditBox searchBox;
    private TrackListWidget trackList;
    private VolumeSlider volumeSlider;
    private ProgressSlider progressSlider;
    private Button playPauseButton;
    private Button stopButton;

    // Line-In tab widgets
    private Button deviceButton;
    private Button startBroadcastButton;
    private Button stopBroadcastButton;
    private List<AudioInputDevice> availableDevices;
    private int selectedDeviceIndex = -1;

    @Nullable
    private AudioResource selectedTrack;
    private float currentVolume = DEFAULT_VOLUME;
    private boolean isBroadcasting = false;

    public SpeakerScreen(SpeakerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();

        tracksWidgets.clear();
        lineInWidgets.clear();

        currentVolume = ClientSpeakerManager.getInstance().getSpeakerVolume(menu.getSpeakerPos());
        isBroadcasting = LiveInputManager.getInstance().isBroadcasting();
        availableDevices = LiveInputManager.getAvailableDevices();

        initTracksTab();
        initLineInTab();

        updateTabVisibility();
        refreshTrackList();
    }

    private void initTracksTab() {
        int leftX = leftPos + PADDING;
        int rightX = leftPos + PADDING + LEFT_COL_WIDTH + GAP;
        int rightWidth = imageWidth - PADDING * 2 - LEFT_COL_WIDTH - GAP;
        int topY = topPos + PADDING;

        // Left column: Search + Track list
        searchBox = new EditBox(font, leftX, topY, LEFT_COL_WIDTH, SEARCH_HEIGHT, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search tracks..."));
        searchBox.setResponder(this::onSearchChanged);
        searchBox.setMaxLength(100);
        addRenderableWidget(searchBox);
        tracksWidgets.add(searchBox);

        int listY = topY + SEARCH_HEIGHT + 4;
        int listHeight = imageHeight - PADDING * 2 - SEARCH_HEIGHT - 4;
        trackList = new TrackListWidget(
            minecraft, LEFT_COL_WIDTH, listHeight, listY, TRACK_ITEM_HEIGHT,
            this::onTrackSelected, this::onTrackDoubleClicked
        );
        trackList.setX(leftX);
        addRenderableWidget(trackList);
        tracksWidgets.add(trackList);

        // Right column: Progress + Volume + Buttons
        int y = topY + 14 + 24; // Skip "Now Playing" title area

        progressSlider = new ProgressSlider(rightX, y, rightWidth, PROGRESS_HEIGHT, this::onSeek);
        addRenderableWidget(progressSlider);
        tracksWidgets.add(progressSlider);
        y += PROGRESS_HEIGHT + GAP;

        volumeSlider = new VolumeSlider(rightX, y, rightWidth, SLIDER_HEIGHT, currentVolume,
            this::onVolumeChanged, this::onVolumeCommit);
        addRenderableWidget(volumeSlider);
        tracksWidgets.add(volumeSlider);
        y += SLIDER_HEIGHT + GAP;

        int halfWidth = (rightWidth - GAP) / 2;
        PlaybackState state = getCurrentState();

        String playPauseText = state.isPlaying() ? "\u23F8 Pause" : "\u25B6 Play";
        playPauseButton = Button.builder(Component.literal(playPauseText), btn -> togglePlayPause())
            .bounds(rightX, y, halfWidth, BUTTON_HEIGHT)
            .build();
        playPauseButton.active = state.isPlaying() || state.isPaused() || selectedTrack != null;
        addRenderableWidget(playPauseButton);
        tracksWidgets.add(playPauseButton);

        stopButton = Button.builder(Component.literal("\u25A0 Stop"), btn -> stopPlayback())
            .bounds(rightX + halfWidth + GAP, y, halfWidth, BUTTON_HEIGHT)
            .build();
        stopButton.active = !state.isStopped();
        addRenderableWidget(stopButton);
        tracksWidgets.add(stopButton);
    }

    private void initLineInTab() {
        // Centered layout for Line-In, single column
        // Buttons at bottom, text instructions at top
        int centerX = leftPos + imageWidth / 2;
        int contentWidth = 200;
        int x = centerX - contentWidth / 2;

        // Buttons positioned from bottom up
        int bottomY = topPos + imageHeight - PADDING - 12 - BUTTON_HEIGHT - GAP - BUTTON_HEIGHT - GAP;

        // Device selector
        deviceButton = Button.builder(Component.literal(getSelectedDeviceName()), btn -> cycleDevice())
            .bounds(x, bottomY, contentWidth, BUTTON_HEIGHT)
            .build();
        addRenderableWidget(deviceButton);
        lineInWidgets.add(deviceButton);

        // Start/Stop buttons side by side
        int btnY = bottomY + BUTTON_HEIGHT + GAP;
        int btnWidth = (contentWidth - GAP) / 2;
        startBroadcastButton = Button.builder(
            Component.translatable("gui.phonon.speaker.linein.start"),
            btn -> startBroadcast()
        ).bounds(x, btnY, btnWidth, BUTTON_HEIGHT).build();
        addRenderableWidget(startBroadcastButton);
        lineInWidgets.add(startBroadcastButton);

        stopBroadcastButton = Button.builder(
            Component.translatable("gui.phonon.speaker.linein.stop"),
            btn -> stopBroadcast()
        ).bounds(x + btnWidth + GAP, btnY, btnWidth, BUTTON_HEIGHT).build();
        addRenderableWidget(stopBroadcastButton);
        lineInWidgets.add(stopBroadcastButton);

        updateLineInButtons();
    }

    private void updateTabVisibility() {
        boolean isTracks = currentTab == Tab.TRACKS;

        for (AbstractWidget w : tracksWidgets) {
            w.visible = isTracks;
            w.active = isTracks && isWidgetActiveForState(w);
        }

        for (AbstractWidget w : lineInWidgets) {
            w.visible = !isTracks;
            w.active = !isTracks;
        }

        if (!isTracks) {
            updateLineInButtons();
        }
    }

    private boolean isWidgetActiveForState(AbstractWidget w) {
        if (w == playPauseButton) {
            PlaybackState state = getCurrentState();
            return state.isPlaying() || state.isPaused() || selectedTrack != null;
        }
        if (w == stopButton) {
            return !getCurrentState().isStopped();
        }
        return true;
    }

    private void switchTab(Tab tab) {
        if (currentTab != tab) {
            currentTab = tab;
            updateTabVisibility();
        }
    }

    private String getSelectedDeviceName() {
        if (availableDevices == null || availableDevices.isEmpty()) {
            return "No input devices";
        }
        if (selectedDeviceIndex < 0 || selectedDeviceIndex >= availableDevices.size()) {
            return "Select device...";
        }
        String name = availableDevices.get(selectedDeviceIndex).name();
        if (font.width(name) > 180) {
            name = font.plainSubstrByWidth(name, 175) + "...";
        }
        return name;
    }

    private void cycleDevice() {
        if (availableDevices == null || availableDevices.isEmpty()) return;
        selectedDeviceIndex = (selectedDeviceIndex + 1) % availableDevices.size();
        deviceButton.setMessage(Component.literal(getSelectedDeviceName()));
        updateLineInButtons();
    }

    private void updateLineInButtons() {
        boolean hasDevice = selectedDeviceIndex >= 0 && availableDevices != null
            && selectedDeviceIndex < availableDevices.size();
        startBroadcastButton.active = hasDevice && !isBroadcasting;
        stopBroadcastButton.active = isBroadcasting;
    }

    private void startBroadcast() {
        if (selectedDeviceIndex < 0 || availableDevices == null) return;

        AudioInputDevice device = availableDevices.get(selectedDeviceIndex);
        UUID streamId = UUID.randomUUID();

        PlatformHelper.INSTANCE.sendToServer(
            new LiveBroadcastStartPacket(menu.getSpeakerPos(), streamId, device.name())
        );

        LiveInputManager manager = LiveInputManager.getInstance();
        manager.setChunkCallback((id, data) -> {
            PlatformHelper.INSTANCE.sendToServer(new LiveBroadcastChunkPacket(id, data));
        });

        manager.startBroadcast(device.name(), streamId);
        isBroadcasting = true;
        updateLineInButtons();
    }

    private void stopBroadcast() {
        LiveInputManager manager = LiveInputManager.getInstance();
        UUID streamId = manager.getCurrentResourceId();

        manager.stopBroadcast();

        if (streamId != null) {
            PlatformHelper.INSTANCE.sendToServer(new LiveBroadcastEndPacket(streamId));
        }

        isBroadcasting = false;
        updateLineInButtons();
    }

    private PlaybackState getCurrentState() {
        return ClientSpeakerManager.getInstance()
            .getSpeakerState(menu.getSpeakerPos())
            .orElse(PlaybackState.STOPPED);
    }

    private void refreshTrackList() {
        String query = searchBox != null ? searchBox.getValue() : "";
        List<AudioResource> resources = query.isEmpty()
            ? ClientAudioManager.getInstance().getAllResources()
            : ClientAudioManager.getInstance().searchResources(query);
        trackList.updateEntries(resources);
    }

    private void onSearchChanged(String query) {
        refreshTrackList();
    }

    private void onTrackSelected(AudioResource track) {
        selectedTrack = track;
        PlaybackState state = getCurrentState();
        if (!state.isPlaying() && !state.isPaused()) {
            playPauseButton.active = track != null;
        }
    }

    private void onTrackDoubleClicked(AudioResource track) {
        selectedTrack = track;
        playTrack(track);
    }

    private void onVolumeChanged(float volume) {
        currentVolume = volume;
        ClientSpeakerManager.getInstance().updateVolume(menu.getSpeakerPos(), volume);
    }

    private void onVolumeCommit() {
        PlatformHelper.INSTANCE.sendToServer(
            new SpeakerVolumePacket(menu.getSpeakerPos(), currentVolume)
        );
    }

    private void onSeek(float progress) {
        PlaybackState state = getCurrentState();
        if (state.isStopped()) return;

        UUID resourceId = state.resourceId();
        Optional<AudioResource> resourceOpt = ClientAudioManager.getInstance().getResource(resourceId);
        long durationMs = resourceOpt.map(AudioResource::durationMs).orElse(-1L);

        if (durationMs <= 0) return;

        long seekPositionMs = (long) (progress * durationMs);
        PlatformHelper.INSTANCE.sendToServer(
            new SpeakerSeekPacket(menu.getSpeakerPos(), seekPositionMs)
        );
    }

    private void togglePlayPause() {
        PlaybackState state = getCurrentState();

        if (state.isPlaying()) {
            PlatformHelper.INSTANCE.sendToServer(
                new SpeakerControlPacket(menu.getSpeakerPos(), SpeakerControlPacket.Action.PAUSE, state.resourceId())
            );
        } else if (state.isPaused()) {
            PlatformHelper.INSTANCE.sendToServer(
                new SpeakerControlPacket(menu.getSpeakerPos(), SpeakerControlPacket.Action.PLAY, state.resourceId())
            );
        } else if (selectedTrack != null) {
            playTrack(selectedTrack);
        }
    }

    private void playTrack(AudioResource track) {
        PlatformHelper.INSTANCE.sendToServer(
            new SpeakerControlPacket(menu.getSpeakerPos(), SpeakerControlPacket.Action.PLAY, track.id())
        );
    }

    private void stopPlayback() {
        PlatformHelper.INSTANCE.sendToServer(
            new SpeakerControlPacket(menu.getSpeakerPos(), SpeakerControlPacket.Action.STOP, null)
        );
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        if (currentTab == Tab.TRACKS) {
            updatePlaybackUI();
        }

        boolean nowBroadcasting = LiveInputManager.getInstance().isBroadcasting();
        if (nowBroadcasting != isBroadcasting) {
            isBroadcasting = nowBroadcasting;
            if (currentTab == Tab.LINE_IN) {
                updateLineInButtons();
            }
        }
    }

    private void updatePlaybackUI() {
        PlaybackState state = getCurrentState();

        if (state.isPlaying() || state.isPaused()) {
            UUID resourceId = state.resourceId();
            Optional<AudioResource> resourceOpt = ClientAudioManager.getInstance().getResource(resourceId);
            long durationMs = resourceOpt.map(AudioResource::durationMs).orElse(-1L);
            long positionMs = state.getCurrentPositionMs(System.currentTimeMillis());
            progressSlider.update(positionMs, durationMs);
        } else {
            progressSlider.update(0, -1);
        }

        if (state.isPlaying()) {
            playPauseButton.setMessage(Component.literal("\u23F8 Pause"));
            playPauseButton.active = true;
        } else if (state.isPaused()) {
            playPauseButton.setMessage(Component.literal("\u25B6 Play"));
            playPauseButton.active = true;
        } else {
            playPauseButton.setMessage(Component.literal("\u25B6 Play"));
            playPauseButton.active = selectedTrack != null;
        }

        stopButton.active = !state.isStopped();
    }

    // ========== Rendering ==========

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Main GUI background
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0101820);
        renderBorder(graphics);

        // Render external tabs
        renderTabs(graphics, mouseX, mouseY);

        // Column divider (only in Tracks mode)
        if (currentTab == Tab.TRACKS) {
            int dividerX = leftPos + PADDING + LEFT_COL_WIDTH + GAP / 2;
            graphics.fill(dividerX, topPos + PADDING, dividerX + 1, topPos + imageHeight - PADDING, 0x40FFFFFF);
        }
    }

    private void renderBorder(GuiGraphics graphics) {
        int x1 = leftPos, y1 = topPos, x2 = leftPos + imageWidth, y2 = topPos + imageHeight;
        int borderColor = 0xFF2A3540;
        graphics.fill(x1, y1, x2, y1 + 1, borderColor);
        graphics.fill(x1, y2 - 1, x2, y2, borderColor);
        graphics.fill(x1, y1, x1 + 1, y2, borderColor);
        graphics.fill(x2 - 1, y1, x2, y2, borderColor);
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int tabX = leftPos - TAB_WIDTH;
        int tabY = topPos + 10;

        // Tab 1: Tracks
        renderTab(graphics, tabX, tabY, currentTab == Tab.TRACKS, Items.NOTE_BLOCK.getDefaultInstance(), mouseX, mouseY);

        // Tab 2: Line-In
        tabY += TAB_HEIGHT + TAB_GAP;
        renderTab(graphics, tabX, tabY, currentTab == Tab.LINE_IN, Items.SCULK_SENSOR.getDefaultInstance(), mouseX, mouseY);
    }

    private void renderTab(GuiGraphics graphics, int x, int y, boolean selected, ItemStack icon, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + TAB_WIDTH && mouseY >= y && mouseY < y + TAB_HEIGHT;

        int bgColor;
        if (selected) {
            bgColor = 0xE0101820; // Same as main GUI
        } else if (hovered) {
            bgColor = 0xC0202030;
        } else {
            bgColor = 0xA0181825;
        }

        graphics.fill(x, y, x + TAB_WIDTH, y + TAB_HEIGHT, bgColor);

        // Border
        int borderColor = selected ? 0xFF2A3540 : 0xFF1A2530;
        graphics.fill(x, y, x + TAB_WIDTH, y + 1, borderColor);
        graphics.fill(x, y + TAB_HEIGHT - 1, x + TAB_WIDTH, y + TAB_HEIGHT, borderColor);
        graphics.fill(x, y, x + 1, y + TAB_HEIGHT, borderColor);

        // No right border if selected (connects to main GUI)
        if (!selected) {
            graphics.fill(x + TAB_WIDTH - 1, y, x + TAB_WIDTH, y + TAB_HEIGHT, borderColor);
        }

        // Icon centered
        int iconX = x + (TAB_WIDTH - 16) / 2;
        int iconY = y + (TAB_HEIGHT - 16) / 2;
        graphics.renderItem(icon, iconX, iconY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        if (currentTab == Tab.TRACKS) {
            renderNowPlaying(graphics);
        } else {
            renderLineInContent(graphics, mouseX, mouseY);
        }
    }

    private void renderNowPlaying(GuiGraphics graphics) {
        int rightX = leftPos + PADDING + LEFT_COL_WIDTH + GAP;
        int rightWidth = imageWidth - PADDING * 2 - LEFT_COL_WIDTH - GAP;
        int y = topPos + PADDING;

        graphics.drawString(font, "Now Playing", rightX, y, 0xFFFFFF);
        y += 14;

        PlaybackState state = getCurrentState();

        if (state.isPlaying()) {
            UUID resourceId = state.resourceId();
            Optional<AudioResource> resourceOpt = ClientAudioManager.getInstance().getResource(resourceId);
            String trackName = resourceOpt.map(AudioResource::name).orElse("Unknown");
            if (font.width(trackName) > rightWidth) {
                trackName = font.plainSubstrByWidth(trackName, rightWidth - 10) + "...";
            }
            graphics.drawString(font, trackName, rightX, y, 0x40FF40);
        } else if (state.isPaused()) {
            UUID resourceId = state.resourceId();
            Optional<AudioResource> resourceOpt = ClientAudioManager.getInstance().getResource(resourceId);
            String trackName = resourceOpt.map(AudioResource::name).orElse("Unknown");
            if (font.width(trackName) > rightWidth - 60) {
                trackName = font.plainSubstrByWidth(trackName, rightWidth - 70) + "...";
            }
            graphics.drawString(font, trackName + " (Paused)", rightX, y, 0xFFFF40);
        } else {
            graphics.drawString(font, "Nothing playing", rightX, y, 0x606060);
        }
    }

    private void renderLineInContent(GuiGraphics graphics, int mouseX, int mouseY) {
        int centerX = leftPos + imageWidth / 2;
        int contentWidth = 240;
        int x = centerX - contentWidth / 2;
        int y = topPos + PADDING;

        // Title
        String title = "Live Input";
        int titleWidth = font.width(title);
        graphics.drawString(font, title, centerX - titleWidth / 2, y, 0xFFFFFF);
        y += 12;

        // Description
        graphics.drawString(font, "Stream phone audio to this speaker.", x, y, 0x808080);
        y += 12;

        // Setup instructions
        graphics.drawString(font, "Setup (Windows):", x, y, 0xCCCCCC);
        y += 10;

        // Step 1: Bluetooth Audio Receiver
        graphics.drawString(font, "1. Install", x + 4, y, 0x707070);
        String link1 = "Bluetooth Audio Receiver";
        int link1X = x + 4 + font.width("1. Install ");
        int link1Width = font.width(link1);
        boolean hover1 = mouseX >= link1X && mouseX <= link1X + link1Width && mouseY >= y && mouseY <= y + 9;
        graphics.drawString(font, link1, link1X, y, hover1 ? 0x80FFFF : 0x40A0FF);
        if (hover1) graphics.fill(link1X, y + 9, link1X + link1Width, y + 10, 0xFF40A0FF);
        y += 10;

        // Step 2: VB-Cable
        graphics.drawString(font, "2. Install", x + 4, y, 0x707070);
        String link2 = "VB-Cable";
        int link2X = x + 4 + font.width("2. Install ");
        int link2Width = font.width(link2);
        boolean hover2 = mouseX >= link2X && mouseX <= link2X + link2Width && mouseY >= y && mouseY <= y + 9;
        graphics.drawString(font, link2, link2X, y, hover2 ? 0x80FFFF : 0x40A0FF);
        if (hover2) graphics.fill(link2X, y + 9, link2X + link2Width, y + 10, 0xFF40A0FF);
        graphics.drawString(font, " (virtual audio cable)", link2X + link2Width, y, 0x606060);
        y += 10;

        // Step 3
        graphics.drawString(font, "3. Set CABLE Input as Windows default output", x + 4, y, 0x707070);
        y += 10;

        // Step 4
        graphics.drawString(font, "4. Connect phone to BT Audio Receiver", x + 4, y, 0x707070);
        y += 10;

        // Step 5
        graphics.drawString(font, "5. Select CABLE Output below & Start", x + 4, y, 0x707070);

        // Status at bottom (above buttons)
        int statusY = topPos + imageHeight - PADDING - 12 - BUTTON_HEIGHT - GAP - BUTTON_HEIGHT - GAP - 14;
        String statusKey = isBroadcasting ? "gui.phonon.speaker.linein.status.active" : "gui.phonon.speaker.linein.status.idle";
        int statusColor = isBroadcasting ? 0x40FF40 : 0x707070;
        String statusText = Component.translatable(statusKey).getString();
        int statusWidth = font.width(statusText);
        graphics.drawString(font, statusText, centerX - statusWidth / 2, statusY, statusColor);
    }

    private void renderWrappedText(GuiGraphics graphics, String text, int x, int y, int maxWidth, int color) {
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();

        for (String word : text.split(" ")) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            if (font.width(testLine) > maxWidth) {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    lines.add(font.plainSubstrByWidth(word, maxWidth - 5) + "...");
                    currentLine = new StringBuilder();
                }
            } else {
                currentLine = new StringBuilder(testLine);
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        for (String line : lines) {
            graphics.drawString(font, line, x, y, color);
            y += 10;
        }
    }

    // ========== Input Handling ==========

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Handle external tab clicks
            int tabX = leftPos - TAB_WIDTH;
            int tabY = topPos + 10;

            if (mouseX >= tabX && mouseX < tabX + TAB_WIDTH) {
                if (mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
                    switchTab(Tab.TRACKS);
                    return true;
                }
                tabY += TAB_HEIGHT + TAB_GAP;
                if (mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
                    switchTab(Tab.LINE_IN);
                    return true;
                }
            }

            // Handle Line-In link clicks
            if (currentTab == Tab.LINE_IN) {
                int centerX = leftPos + imageWidth / 2;
                int contentWidth = 240;
                int x = centerX - contentWidth / 2;
                // Link positions: PADDING + 12 (title) + 12 (desc) + 10 (setup header)
                int baseY = topPos + PADDING + 12 + 12 + 10;

                // Link 1: Bluetooth Audio Receiver
                int link1X = x + 4 + font.width("1. Install ");
                String link1 = "Bluetooth Audio Receiver";
                int link1Width = font.width(link1);
                if (mouseX >= link1X && mouseX <= link1X + link1Width && mouseY >= baseY && mouseY <= baseY + 9) {
                    Util.getPlatform().openUri(BT_RECEIVER_LINK);
                    return true;
                }

                // Link 2: VB-Cable (10 pixels below link1)
                int link2Y = baseY + 10;
                int link2X = x + 4 + font.width("2. Install ");
                String link2 = "VB-Cable";
                int link2Width = font.width(link2);
                if (mouseX >= link2X && mouseX <= link2X + link2Width && mouseY >= link2Y && mouseY <= link2Y + 9) {
                    Util.getPlatform().openUri(VB_CABLE_LINK);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Disable default inventory labels
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            if (keyCode == 256) { // ESC
                searchBox.setFocused(false);
                return true;
            }
            return searchBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            return searchBox.charTyped(c, modifiers);
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (getFocused() != null && isDragging() && button == 0) {
            return getFocused().mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
}
