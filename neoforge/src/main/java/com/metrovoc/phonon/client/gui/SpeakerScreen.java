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
 * Speaker control GUI with dual-column layout.
 *
 * Left column: Search + Track list
 * Right column: Now playing + Progress + Volume + Play/Stop
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
    private Button playStopButton;

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

        // Initialize volume from speaker (persisted per-speaker, not per-track)
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
        // Title area (rendered in render())
        y += 14;

        // Search box
        searchBox = new EditBox(font, x, y, width, SEARCH_HEIGHT, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search tracks..."));
        searchBox.setResponder(this::onSearchChanged);
        searchBox.setMaxLength(100);
        addRenderableWidget(searchBox);
        y += SEARCH_HEIGHT + 4;

        // Track list (fill remaining space)
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
        // "Now Playing" label area (rendered in render())
        y += 14;

        // Now playing info area (rendered in render())
        y += 24;

        // Progress slider
        progressSlider = new ProgressSlider(x, y, width, PROGRESS_HEIGHT, this::onSeek);
        addRenderableWidget(progressSlider);
        y += PROGRESS_HEIGHT + GAP;

        // Volume slider - onChange for real-time local audio, onCommit for server sync
        volumeSlider = new VolumeSlider(x, y, width, SLIDER_HEIGHT, currentVolume,
            this::onVolumeChanged, this::onVolumeCommit);
        addRenderableWidget(volumeSlider);
        y += SLIDER_HEIGHT + GAP;

        // Play/Stop button - initialize with correct state to avoid flicker
        boolean playing = isPlaying();
        String buttonText = playing ? "\u25A0 Stop" : "\u25B6 Play";
        playStopButton = Button.builder(Component.literal(buttonText), btn -> togglePlayback())
            .bounds(x, y, width, BUTTON_HEIGHT)
            .build();
        playStopButton.active = playing || selectedTrack != null;
        addRenderableWidget(playStopButton);
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
        // Enable play button when a track is selected (if not already playing)
        if (!isPlaying()) {
            playStopButton.active = track != null;
        }
    }

    private void onTrackDoubleClicked(AudioResource track) {
        selectedTrack = track;
        playTrack(track);
    }

    private void onVolumeChanged(float volume) {
        currentVolume = volume;
        // Real-time local audio update while dragging
        ClientSpeakerManager.getInstance().updateVolume(menu.getSpeakerPos(), volume);
    }

    private void onVolumeCommit() {
        // Send final volume to server on release (persists and syncs to other players)
        PlatformHelper.INSTANCE.sendToServer(
            new SpeakerVolumePacket(menu.getSpeakerPos(), currentVolume)
        );
    }

    private void onSeek(float progress) {
        Optional<PlaybackState> stateOpt = ClientSpeakerManager.getInstance().getSpeakerState(menu.getSpeakerPos());
        if (stateOpt.isEmpty() || !stateOpt.get().playing()) return;

        UUID resourceId = stateOpt.get().resourceId();
        Optional<AudioResource> resourceOpt = ClientAudioManager.getInstance().getResource(resourceId);
        long durationMs = resourceOpt.map(AudioResource::durationMs).orElse(-1L);

        if (durationMs <= 0) return;

        long seekPositionMs = (long) (progress * durationMs);
        PlatformHelper.INSTANCE.sendToServer(
            new SpeakerSeekPacket(menu.getSpeakerPos(), seekPositionMs)
        );
    }

    private void togglePlayback() {
        if (isPlaying()) {
            stopPlayback();
        } else if (selectedTrack != null) {
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

    private boolean isPlaying() {
        return ClientSpeakerManager.getInstance()
            .getSpeakerState(menu.getSpeakerPos())
            .map(PlaybackState::playing)
            .orElse(false);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updatePlaybackUI();
    }

    private void updatePlaybackUI() {
        Optional<PlaybackState> stateOpt = ClientSpeakerManager.getInstance().getSpeakerState(menu.getSpeakerPos());
        boolean playing = stateOpt.map(PlaybackState::playing).orElse(false);

        // Update progress and check for playback completion
        if (playing && stateOpt.isPresent()) {
            PlaybackState state = stateOpt.get();
            UUID resourceId = state.resourceId();
            Optional<AudioResource> resourceOpt = ClientAudioManager.getInstance().getResource(resourceId);
            long durationMs = resourceOpt.map(AudioResource::durationMs).orElse(-1L);
            long positionMs = state.getCurrentPositionMs(System.currentTimeMillis());

            // Auto-stop when playback reaches end
            if (durationMs > 0 && positionMs >= durationMs) {
                stopPlayback();
                playing = false;
                progressSlider.update(durationMs, durationMs);
            } else {
                progressSlider.update(positionMs, durationMs);
            }
        } else {
            progressSlider.update(0, -1);
        }

        // Update button text and state
        if (playing) {
            playStopButton.setMessage(Component.literal("\u25A0 Stop"));
            playStopButton.active = true;
        } else {
            playStopButton.setMessage(Component.literal("\u25B6 Play"));
            playStopButton.active = selectedTrack != null;
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Main background
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0101820);

        // Border
        renderBorder(graphics);

        // Column divider
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

        // Left column title
        graphics.drawString(font, "Tracks", leftX, topPos + PADDING, 0xFFFFFF);
        String countText = ClientAudioManager.getInstance().getAllResources().size() + " total";
        int countWidth = font.width(countText);
        graphics.drawString(font, countText, leftX + leftWidth - countWidth, topPos + PADDING, 0x808080);

        // Right column: Now Playing
        renderNowPlaying(graphics, rightX, topPos + PADDING, rightWidth);
    }

    private void renderNowPlaying(GuiGraphics graphics, int x, int y, int width) {
        graphics.drawString(font, "Now Playing", x, y, 0xFFFFFF);
        y += 14;

        Optional<PlaybackState> stateOpt = ClientSpeakerManager.getInstance().getSpeakerState(menu.getSpeakerPos());

        if (stateOpt.isPresent() && stateOpt.get().playing()) {
            PlaybackState state = stateOpt.get();
            UUID resourceId = state.resourceId();
            Optional<AudioResource> resourceOpt = ClientAudioManager.getInstance().getResource(resourceId);
            String trackName = resourceOpt.map(AudioResource::name).orElse("Unknown");

            if (font.width(trackName) > width) {
                trackName = font.plainSubstrByWidth(trackName, width - 10) + "...";
            }
            graphics.drawString(font, trackName, x, y, 0x40FF40);
        } else {
            graphics.drawString(font, "Nothing playing", x, y, 0x606060);
        }
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
        // AbstractContainerScreen.mouseDragged handles slot dragging but doesn't call super,
        // so widgets never receive drag events. We must dispatch manually.
        if (getFocused() != null && isDragging() && button == 0) {
            return getFocused().mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
}
