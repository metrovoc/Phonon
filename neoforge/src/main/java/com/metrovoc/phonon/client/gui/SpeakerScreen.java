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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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
        super(menu, playerInv, title, 360, 240);
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

        searchBox = new EditBox(font, x, y, width, SEARCH_HEIGHT, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search tracks..."));
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
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0101820);
        extractBorder(graphics);

        int dividerX = leftPos + PADDING + (int) ((imageWidth - PADDING * 3) * LEFT_RATIO) + PADDING / 2;
        graphics.fill(dividerX, topPos + PADDING, dividerX + 1, topPos + imageHeight - PADDING, 0x40FFFFFF);
    }

    private void extractBorder(GuiGraphicsExtractor graphics) {
        int x1 = leftPos, y1 = topPos, x2 = leftPos + imageWidth, y2 = topPos + imageHeight;
        int borderColor = 0xFF2A3540;
        graphics.fill(x1, y1, x2, y1 + 1, borderColor);
        graphics.fill(x1, y2 - 1, x2, y2, borderColor);
        graphics.fill(x1, y1, x1 + 1, y2, borderColor);
        graphics.fill(x2 - 1, y1, x2, y2, borderColor);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int leftWidth = (int) ((imageWidth - PADDING * 3) * LEFT_RATIO);
        int leftX = leftPos + PADDING;
        int rightX = leftX + leftWidth + PADDING;
        int rightWidth = imageWidth - PADDING * 3 - leftWidth;

        graphics.text(font, "Tracks", leftX, topPos + PADDING, 0xFFFFFF);
        String countText = ClientAudioManager.getInstance().getAllResources().size() + " total";
        int countWidth = font.width(countText);
        graphics.text(font, countText, leftX + leftWidth - countWidth, topPos + PADDING, 0x808080);

        extractNowPlaying(graphics, rightX, topPos + PADDING, rightWidth);
    }

    private void extractNowPlaying(GuiGraphicsExtractor graphics, int x, int y, int width) {
        graphics.text(font, "Now Playing", x, y, 0xFFFFFF);
        y += 14;

        PlaybackState state = getCurrentState();

        if (state.isPlaying()) {
            UUID resourceId = state.resourceId();
            Optional<AudioResource> resourceOpt = ClientAudioManager.getInstance().getResource(resourceId);
            String trackName = resourceOpt.map(AudioResource::name).orElse("Unknown");

            if (font.width(trackName) > width) {
                trackName = font.plainSubstrByWidth(trackName, width - 10) + "...";
            }
            graphics.text(font, trackName, x, y, 0x40FF40);
        } else if (state.isPaused()) {
            UUID resourceId = state.resourceId();
            Optional<AudioResource> resourceOpt = ClientAudioManager.getInstance().getResource(resourceId);
            String trackName = resourceOpt.map(AudioResource::name).orElse("Unknown");

            if (font.width(trackName) > width) {
                trackName = font.plainSubstrByWidth(trackName, width - 10) + "...";
            }
            graphics.text(font, trackName + " (Paused)", x, y, 0xFFFF40);
        } else {
            graphics.text(font, "Nothing playing", x, y, 0x606060);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // 禁用默认 inventory 标签
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (searchBox != null && searchBox.isFocused()) {
            if (event.isEscape()) {
                searchBox.setFocused(false);
                return true;
            }
            return searchBox.keyPressed(event);
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (searchBox != null && searchBox.isFocused()) {
            return searchBox.charTyped(event);
        }
        return super.charTyped(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (getFocused() != null && isDragging() && event.button() == 0) {
            return getFocused().mouseDragged(event, dragX, dragY);
        }
        return super.mouseDragged(event, dragX, dragY);
    }
}
