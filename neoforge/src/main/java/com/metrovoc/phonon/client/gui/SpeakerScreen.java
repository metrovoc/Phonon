package com.metrovoc.phonon.client.gui;

import com.metrovoc.phonon.audio.AudioResource;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.client.ClientAudioManager;
import com.metrovoc.phonon.client.ClientSpeakerManager;
import com.metrovoc.phonon.menu.SpeakerMenu;
import com.metrovoc.phonon.network.packets.SpeakerControlPacket;
import com.metrovoc.phonon.network.packets.SpeakerSeekPacket;
import com.metrovoc.phonon.network.packets.SpeakerVolumePacket;
import com.metrovoc.phonon.platform.PlatformHelper;
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
 * 左栏: 搜索 + 曲目列表
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

    private static final float DEFAULT_VOLUME = 0.5f;
    private static final float LEFT_RATIO = 0.55f;

    private static final Component PLAY = Component.translatable("gui.phonon.speaker.play");
    private static final Component PAUSE = Component.translatable("gui.phonon.speaker.pause");
    private static final Component STOP = Component.translatable("gui.phonon.speaker.stop");

    private EditBox searchBox;
    private TrackListWidget trackList;
    private VolumeSlider volumeSlider;
    private ProgressSlider progressSlider;
    private Button playPauseButton;
    private Button stopButton;

    @Nullable
    private AudioResource selectedTrack;
    private float currentVolume = DEFAULT_VOLUME;

    public SpeakerScreen(SpeakerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 360;
        this.imageHeight = 240;
    }

    @Override
    protected void init() {
        super.init();

        currentVolume = ClientSpeakerManager.getInstance().getSpeakerVolume(menu.getSpeakerPos());

        int leftWidth = (int) ((imageWidth - PADDING * 3) * LEFT_RATIO);
        int rightWidth = imageWidth - PADDING * 3 - leftWidth;
        int leftX = leftPos + PADDING;
        int rightX = leftX + leftWidth + PADDING;
        int topY = topPos + PADDING;

        initLeftColumn(leftX, topY, leftWidth);
        initRightColumn(rightX, topY, rightWidth);
        refreshTrackList();
    }

    private void initLeftColumn(int x, int y, int width) {
        y += 14;

        searchBox = new EditBox(font, x, y, width, SEARCH_HEIGHT, Component.translatable("gui.phonon.speaker.search"));
        searchBox.setHint(Component.translatable("gui.phonon.speaker.search_hint"));
        searchBox.setResponder(this::onSearchChanged);
        searchBox.setMaxLength(100);
        addRenderableWidget(searchBox);
        y += SEARCH_HEIGHT + 4;

        int listHeight = imageHeight - (y - topPos) - PADDING;
        trackList = new TrackListWidget(
            minecraft, width, listHeight, y, TRACK_ITEM_HEIGHT,
            this::onTrackSelected,
            this::onTrackDoubleClicked
        );
        trackList.setX(x);
        addRenderableWidget(trackList);
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

        playPauseButton = Button.builder(state.isPlaying() ? PAUSE : PLAY, btn -> togglePlayPause())
            .bounds(x, y, halfWidth, BUTTON_HEIGHT)
            .build();
        playPauseButton.active = state.isPlaying() || state.isPaused() || selectedTrack != null;
        addRenderableWidget(playPauseButton);

        // Stop 按钮
        stopButton = Button.builder(STOP, btn -> stopPlayback())
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
    }

    private void updatePlaybackUI() {
        PlaybackState state = getCurrentState();

        // 更新进度条
        if (state.isPlaying() || state.isPaused()) {
            UUID resourceId = state.resourceId();
            Optional<AudioResource> resourceOpt = ClientAudioManager.getInstance().getResource(resourceId);
            long durationMs = resourceOpt.map(AudioResource::durationMs).orElse(-1L);
            long positionMs = state.getCurrentPositionMs();

            progressSlider.update(positionMs, durationMs);
        } else {
            progressSlider.update(0, -1);
        }

        // 更新 Play/Pause 按钮
        if (state.isPlaying()) {
            playPauseButton.setMessage(PAUSE);
            playPauseButton.active = true;
        } else if (state.isPaused()) {
            playPauseButton.setMessage(PLAY);
            playPauseButton.active = true;
        } else {
            playPauseButton.setMessage(PLAY);
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

        graphics.drawString(font, Component.translatable("gui.phonon.speaker.tracks"), leftX, topPos + PADDING, 0xFFFFFFFF);
        Component countText = Component.translatable("gui.phonon.speaker.total",
            ClientAudioManager.getInstance().getAllResources().size());
        int countWidth = font.width(countText);
        graphics.drawString(font, countText, leftX + leftWidth - countWidth, topPos + PADDING, 0xFF808080);

        renderNowPlaying(graphics, rightX, topPos + PADDING, rightWidth);
    }

    private void renderNowPlaying(GuiGraphics graphics, int x, int y, int width) {
        graphics.drawString(font, Component.translatable("gui.phonon.speaker.now_playing"), x, y, 0xFFFFFFFF);
        y += 14;

        PlaybackState state = getCurrentState();

        if (state.isPlaying()) {
            UUID resourceId = state.resourceId();
            Optional<AudioResource> resourceOpt = ClientAudioManager.getInstance().getResource(resourceId);
            String trackName = resourceOpt.map(AudioResource::name).orElseGet(() -> Component.translatable("gui.phonon.speaker.unknown").getString());

            if (font.width(trackName) > width) {
                trackName = font.plainSubstrByWidth(trackName, width - 10) + "...";
            }
            graphics.drawString(font, trackName, x, y, 0xFF40FF40);
        } else if (state.isPaused()) {
            UUID resourceId = state.resourceId();
            Optional<AudioResource> resourceOpt = ClientAudioManager.getInstance().getResource(resourceId);
            String trackName = resourceOpt.map(AudioResource::name).orElseGet(() -> Component.translatable("gui.phonon.speaker.unknown").getString());

            if (font.width(trackName) > width) {
                trackName = font.plainSubstrByWidth(trackName, width - 10) + "...";
            }
            graphics.drawString(font, Component.translatable("gui.phonon.speaker.paused", trackName), x, y, 0xFFFFFF40);
        } else {
            graphics.drawString(font, Component.translatable("gui.phonon.speaker.nothing_playing"), x, y, 0xFF606060);
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
