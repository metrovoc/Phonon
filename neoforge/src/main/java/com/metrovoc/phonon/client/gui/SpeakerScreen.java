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
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Speaker 控制 GUI。
 * 左栏: Tab 切换 (Tracks / Line-In)
 * 右栏: 当前播放 + 进度条 + 音量 + Play/Pause/Stop
 */
public class SpeakerScreen extends AbstractContainerScreen<SpeakerMenu> {
    private static final int PADDING = 8;
    private static final int GAP = 8;
    private static final int SEARCH_HEIGHT = 20;
    private static final int TRACK_ITEM_HEIGHT = 18;
    private static final int SLIDER_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 20;
    private static final int PROGRESS_HEIGHT = 24;
    private static final int TAB_HEIGHT = 16;

    private static final float DEFAULT_VOLUME = 0.5f;
    private static final float LEFT_RATIO = 0.55f;

    private static final String WINDOWS_STORE_LINK = "ms-windows-store://pdp/?productid=9N9WCLWDQS5J";
    private static final String VB_CABLE_LINK = "https://vb-audio.com/Cable/";

    private enum Tab { TRACKS, LINE_IN }
    private Tab currentTab = Tab.TRACKS;

    // Tab buttons
    private Button tabTracksButton;
    private Button tabLineInButton;

    // Tracks tab widgets
    private EditBox searchBox;
    private TrackListWidget trackList;

    // Line-In tab widgets
    private Button deviceButton;
    private Button startBroadcastButton;
    private Button stopBroadcastButton;
    private List<AudioInputDevice> availableDevices;
    private int selectedDeviceIndex = -1;

    // Right column (shared)
    private VolumeSlider volumeSlider;
    private ProgressSlider progressSlider;
    private Button playPauseButton;
    private Button stopButton;

    @Nullable
    private AudioResource selectedTrack;
    private float currentVolume = DEFAULT_VOLUME;
    private boolean isBroadcasting = false;

    public SpeakerScreen(SpeakerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 360;
        this.imageHeight = 240;
    }

    @Override
    protected void init() {
        super.init();

        currentVolume = ClientSpeakerManager.getInstance().getSpeakerVolume(menu.getSpeakerPos());
        isBroadcasting = LiveInputManager.getInstance().isBroadcasting();
        availableDevices = LiveInputManager.getAvailableDevices();

        int leftWidth = (int) ((imageWidth - PADDING * 3) * LEFT_RATIO);
        int rightWidth = imageWidth - PADDING * 3 - leftWidth;
        int leftX = leftPos + PADDING;
        int rightX = leftX + leftWidth + PADDING;
        int topY = topPos + PADDING;

        initTabs(leftX, topY, leftWidth);
        initTracksTab(leftX, topY + TAB_HEIGHT + GAP, leftWidth);
        initLineInTab(leftX, topY + TAB_HEIGHT + GAP, leftWidth);
        initRightColumn(rightX, topY, rightWidth);

        updateTabVisibility();
        refreshTrackList();
    }

    private void initTabs(int x, int y, int width) {
        int tabWidth = (width - GAP) / 2;

        tabTracksButton = Button.builder(Component.translatable("gui.phonon.speaker.tab.tracks"), btn -> switchTab(Tab.TRACKS))
            .bounds(x, y, tabWidth, TAB_HEIGHT)
            .build();
        addRenderableWidget(tabTracksButton);

        tabLineInButton = Button.builder(Component.translatable("gui.phonon.speaker.tab.linein"), btn -> switchTab(Tab.LINE_IN))
            .bounds(x + tabWidth + GAP, y, tabWidth, TAB_HEIGHT)
            .build();
        addRenderableWidget(tabLineInButton);
    }

    private void switchTab(Tab tab) {
        currentTab = tab;
        updateTabVisibility();
    }

    private void updateTabVisibility() {
        boolean showTracks = currentTab == Tab.TRACKS;
        boolean showLineIn = currentTab == Tab.LINE_IN;

        // Tab button styling
        tabTracksButton.active = !showTracks;
        tabLineInButton.active = !showLineIn;

        // Tracks tab widgets
        searchBox.visible = showTracks;
        trackList.visible = showTracks;

        // Line-In tab widgets
        deviceButton.visible = showLineIn;
        startBroadcastButton.visible = showLineIn;
        stopBroadcastButton.visible = showLineIn;

        updateLineInButtons();
    }

    private void initTracksTab(int x, int y, int width) {
        searchBox = new EditBox(font, x, y, width, SEARCH_HEIGHT, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search tracks..."));
        searchBox.setResponder(this::onSearchChanged);
        searchBox.setMaxLength(100);
        addRenderableWidget(searchBox);

        int listY = y + SEARCH_HEIGHT + 4;
        int listHeight = imageHeight - (listY - topPos) - PADDING;
        trackList = new TrackListWidget(
            minecraft, width, listHeight, listY, TRACK_ITEM_HEIGHT,
            this::onTrackSelected,
            this::onTrackDoubleClicked
        );
        trackList.setX(x);
        addRenderableWidget(trackList);
    }

    private void initLineInTab(int x, int y, int width) {
        // Content area starts after description text (approximately 100 pixels down)
        int contentY = y + 90;

        // Device selector button
        String deviceText = getSelectedDeviceName();
        deviceButton = Button.builder(Component.literal(deviceText), btn -> cycleDevice())
            .bounds(x, contentY, width, BUTTON_HEIGHT)
            .build();
        addRenderableWidget(deviceButton);

        // Start/Stop buttons
        int btnWidth = (width - GAP) / 2;
        int btnY = contentY + BUTTON_HEIGHT + GAP;

        startBroadcastButton = Button.builder(
            Component.translatable("gui.phonon.speaker.linein.start"),
            btn -> startBroadcast()
        ).bounds(x, btnY, btnWidth, BUTTON_HEIGHT).build();
        addRenderableWidget(startBroadcastButton);

        stopBroadcastButton = Button.builder(
            Component.translatable("gui.phonon.speaker.linein.stop"),
            btn -> stopBroadcast()
        ).bounds(x + btnWidth + GAP, btnY, btnWidth, BUTTON_HEIGHT).build();
        addRenderableWidget(stopBroadcastButton);
    }

    private String getSelectedDeviceName() {
        if (availableDevices == null || availableDevices.isEmpty()) {
            return "No input devices found";
        }
        if (selectedDeviceIndex < 0 || selectedDeviceIndex >= availableDevices.size()) {
            return "Select device...";
        }
        String name = availableDevices.get(selectedDeviceIndex).name();
        if (font.width(name) > 150) {
            name = font.plainSubstrByWidth(name, 145) + "...";
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

        // Send start packet to server
        PlatformHelper.INSTANCE.sendToServer(
            new LiveBroadcastStartPacket(menu.getSpeakerPos(), streamId, device.name())
        );

        // Setup callbacks to forward audio data to server
        LiveInputManager manager = LiveInputManager.getInstance();
        manager.setChunkCallback((id, data) -> {
            PlatformHelper.INSTANCE.sendToServer(
                new LiveBroadcastChunkPacket(id, data)
            );
        });

        // Start local capture
        manager.startBroadcast(device.name(), streamId);
        isBroadcasting = true;
        updateLineInButtons();
    }

    private void stopBroadcast() {
        LiveInputManager manager = LiveInputManager.getInstance();
        UUID streamId = manager.getCurrentResourceId();

        manager.stopBroadcast();

        // Notify server
        if (streamId != null) {
            PlatformHelper.INSTANCE.sendToServer(new LiveBroadcastEndPacket(streamId));
        }

        isBroadcasting = false;
        updateLineInButtons();
    }

    private void initRightColumn(int x, int y, int width) {
        y += 14;
        y += 24;

        progressSlider = new ProgressSlider(x, y, width, PROGRESS_HEIGHT, this::onSeek);
        addRenderableWidget(progressSlider);
        y += PROGRESS_HEIGHT + GAP;

        volumeSlider = new VolumeSlider(x, y, width, SLIDER_HEIGHT, currentVolume,
            this::onVolumeChanged, this::onVolumeCommit);
        addRenderableWidget(volumeSlider);
        y += SLIDER_HEIGHT + GAP;

        // Play/Pause 按钮
        int halfWidth = (width - GAP) / 2;
        PlaybackState state = getCurrentState();

        String playPauseText = state.isPlaying() ? "\u23F8 Pause" : "\u25B6 Play";
        playPauseButton = Button.builder(Component.literal(playPauseText), btn -> togglePlayPause())
            .bounds(x, y, halfWidth, BUTTON_HEIGHT)
            .build();
        playPauseButton.active = state.isPlaying() || state.isPaused() || selectedTrack != null;
        addRenderableWidget(playPauseButton);

        // Stop 按钮
        stopButton = Button.builder(Component.literal("\u25A0 Stop"), btn -> stopPlayback())
            .bounds(x + halfWidth + GAP, y, halfWidth, BUTTON_HEIGHT)
            .build();
        stopButton.active = !state.isStopped();
        addRenderableWidget(stopButton);
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
            // 正在播放 -> 暂停
            PlatformHelper.INSTANCE.sendToServer(
                new SpeakerControlPacket(
                    menu.getSpeakerPos(),
                    SpeakerControlPacket.Action.PAUSE,
                    state.resourceId()
                )
            );
        } else if (state.isPaused()) {
            // 暂停 -> 恢复播放
            PlatformHelper.INSTANCE.sendToServer(
                new SpeakerControlPacket(
                    menu.getSpeakerPos(),
                    SpeakerControlPacket.Action.PLAY,
                    state.resourceId()
                )
            );
        } else if (selectedTrack != null) {
            // 停止状态 -> 播放选中曲目
            playTrack(selectedTrack);
        }
    }

    private void playTrack(AudioResource track) {
        PlatformHelper.INSTANCE.sendToServer(
            new SpeakerControlPacket(
                menu.getSpeakerPos(),
                SpeakerControlPacket.Action.PLAY,
                track.id()
            )
        );
    }

    private void stopPlayback() {
        PlatformHelper.INSTANCE.sendToServer(
            new SpeakerControlPacket(
                menu.getSpeakerPos(),
                SpeakerControlPacket.Action.STOP,
                null
            )
        );
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updatePlaybackUI();

        // Sync broadcast state
        boolean nowBroadcasting = LiveInputManager.getInstance().isBroadcasting();
        if (nowBroadcasting != isBroadcasting) {
            isBroadcasting = nowBroadcasting;
            updateLineInButtons();
        }
    }

    private void updatePlaybackUI() {
        PlaybackState state = getCurrentState();

        // 更新进度条
        if (state.isPlaying() || state.isPaused()) {
            UUID resourceId = state.resourceId();
            Optional<AudioResource> resourceOpt = ClientAudioManager.getInstance().getResource(resourceId);
            long durationMs = resourceOpt.map(AudioResource::durationMs).orElse(-1L);
            long positionMs = state.getCurrentPositionMs(System.currentTimeMillis());

            progressSlider.update(positionMs, durationMs);
        } else {
            progressSlider.update(0, -1);
        }

        // 更新 Play/Pause 按钮
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

        // 更新 Stop 按钮
        stopButton.active = !state.isStopped();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0101820);
        renderBorder(graphics);

        int dividerX = leftPos + PADDING + (int) ((imageWidth - PADDING * 3) * LEFT_RATIO) + PADDING / 2;
        graphics.fill(dividerX, topPos + PADDING, dividerX + 1, topPos + imageHeight - PADDING, 0x40FFFFFF);
    }

    private void renderBorder(GuiGraphics graphics) {
        int x1 = leftPos, y1 = topPos, x2 = leftPos + imageWidth, y2 = topPos + imageHeight;
        int borderColor = 0xFF2A3540;
        graphics.fill(x1, y1, x2, y1 + 1, borderColor);
        graphics.fill(x1, y2 - 1, x2, y2, borderColor);
        graphics.fill(x1, y1, x1 + 1, y2, borderColor);
        graphics.fill(x2 - 1, y1, x2, y2, borderColor);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int leftWidth = (int) ((imageWidth - PADDING * 3) * LEFT_RATIO);
        int leftX = leftPos + PADDING;
        int rightX = leftX + leftWidth + PADDING;
        int rightWidth = imageWidth - PADDING * 3 - leftWidth;

        if (currentTab == Tab.LINE_IN) {
            renderLineInContent(graphics, leftX, topPos + PADDING + TAB_HEIGHT + GAP, leftWidth, mouseX, mouseY);
        }

        renderNowPlaying(graphics, rightX, topPos + PADDING, rightWidth);
    }

    private void renderLineInContent(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        // Title
        graphics.drawString(font, Component.translatable("gui.phonon.speaker.linein.title"), x, y, 0xFFFFFF);
        y += 12;

        // Description
        graphics.drawString(font, Component.translatable("gui.phonon.speaker.linein.description"), x, y, 0xA0A0A0);
        y += 20;

        // Setup instructions
        graphics.drawString(font, Component.translatable("gui.phonon.speaker.linein.setup"), x, y, 0xFFFFFF);
        y += 12;

        // Step 1 with clickable links
        graphics.drawString(font, Component.translatable("gui.phonon.speaker.linein.step1"), x, y, 0xA0A0A0);
        y += 11;

        // Link 1: Bluetooth Audio Receiver
        String link1Text = "Bluetooth Audio Receiver";
        int link1X = x + 8;
        int link1Width = font.width(link1Text);
        boolean hover1 = mouseX >= link1X && mouseX <= link1X + link1Width && mouseY >= y && mouseY <= y + 9;
        graphics.drawString(font, link1Text, link1X, y, hover1 ? 0x80FFFF : 0x40A0FF);
        if (hover1) {
            graphics.fill(link1X, y + 9, link1X + link1Width, y + 10, 0xFF40A0FF);
        }
        y += 11;

        // "or"
        graphics.drawString(font, "or", x + 8, y, 0x606060);
        y += 11;

        // Link 2: VB-Cable
        String link2Text = "VB-Cable";
        int link2X = x + 8;
        int link2Width = font.width(link2Text);
        boolean hover2 = mouseX >= link2X && mouseX <= link2X + link2Width && mouseY >= y && mouseY <= y + 9;
        graphics.drawString(font, link2Text, link2X, y, hover2 ? 0x80FFFF : 0x40A0FF);
        if (hover2) {
            graphics.fill(link2X, y + 9, link2X + link2Width, y + 10, 0xFF40A0FF);
        }
        y += 14;

        // Status text at bottom
        int statusY = topPos + imageHeight - PADDING - 10;
        String statusKey = isBroadcasting ? "gui.phonon.speaker.linein.status.active" : "gui.phonon.speaker.linein.status.idle";
        int statusColor = isBroadcasting ? 0x40FF40 : 0x808080;
        graphics.drawString(font, Component.translatable(statusKey), x, statusY, statusColor);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && currentTab == Tab.LINE_IN) {
            int leftWidth = (int) ((imageWidth - PADDING * 3) * LEFT_RATIO);
            int x = leftPos + PADDING;
            int y = topPos + PADDING + TAB_HEIGHT + GAP + 12 + 20 + 12 + 11;

            // Check link 1 click
            String link1Text = "Bluetooth Audio Receiver";
            int link1X = x + 8;
            int link1Width = font.width(link1Text);
            if (mouseX >= link1X && mouseX <= link1X + link1Width && mouseY >= y && mouseY <= y + 9) {
                Util.getPlatform().openUri(WINDOWS_STORE_LINK);
                return true;
            }

            y += 11 + 11; // skip "or" line

            // Check link 2 click
            String link2Text = "VB-Cable";
            int link2X = x + 8;
            int link2Width = font.width(link2Text);
            if (mouseX >= link2X && mouseX <= link2X + link2Width && mouseY >= y && mouseY <= y + 9) {
                Util.getPlatform().openUri(VB_CABLE_LINK);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderNowPlaying(GuiGraphics graphics, int x, int y, int width) {
        graphics.drawString(font, "Now Playing", x, y, 0xFFFFFF);
        y += 14;

        PlaybackState state = getCurrentState();

        if (state.isPlaying()) {
            UUID resourceId = state.resourceId();
            Optional<AudioResource> resourceOpt = ClientAudioManager.getInstance().getResource(resourceId);
            String trackName = resourceOpt.map(AudioResource::name).orElse("Unknown");

            if (font.width(trackName) > width) {
                trackName = font.plainSubstrByWidth(trackName, width - 10) + "...";
            }
            graphics.drawString(font, trackName, x, y, 0x40FF40);
        } else if (state.isPaused()) {
            UUID resourceId = state.resourceId();
            Optional<AudioResource> resourceOpt = ClientAudioManager.getInstance().getResource(resourceId);
            String trackName = resourceOpt.map(AudioResource::name).orElse("Unknown");

            if (font.width(trackName) > width) {
                trackName = font.plainSubstrByWidth(trackName, width - 10) + "...";
            }
            graphics.drawString(font, trackName + " (Paused)", x, y, 0xFFFF40);
        } else {
            graphics.drawString(font, "Nothing playing", x, y, 0x606060);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 禁用默认 inventory 标签
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
