package com.metrovoc.phonon.client.gui;

import com.metrovoc.phonon.audio.AudioResource;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.client.ClientAudioManager;
import com.metrovoc.phonon.client.ClientSpeakerManager;
import com.metrovoc.phonon.menu.SpeakerMenu;
import com.metrovoc.phonon.network.packets.SpeakerControlPacket;
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
 * Speaker control GUI.
 *
 * Layout:
 * - Header with title
 * - Search box
 * - Scrollable track list
 * - Playback info (current track + progress)
 * - Volume slider
 * - Control buttons
 */
public class SpeakerScreen extends AbstractContainerScreen<SpeakerMenu> {
    private static final float DEFAULT_VOLUME = 0.25f;

    private static final int PADDING = 8;
    private static final int SEARCH_HEIGHT = 20;
    private static final int TRACK_ITEM_HEIGHT = 18;
    private static final int PLAYBACK_INFO_HEIGHT = 32;
    private static final int SLIDER_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 20;

    private EditBox searchBox;
    private TrackListWidget trackList;
    private VolumeSlider volumeSlider;
    private Button playButton;
    private Button stopButton;

    @Nullable
    private AudioResource selectedTrack;
    private float currentVolume = DEFAULT_VOLUME;

    public SpeakerScreen(SpeakerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 240;
        this.imageHeight = 220;
    }

    @Override
    protected void init() {
        super.init();

        int contentLeft = leftPos + PADDING;
        int contentWidth = imageWidth - PADDING * 2;
        int y = topPos + PADDING + 12; // After title

        // Search box
        searchBox = new EditBox(font, contentLeft, y, contentWidth, SEARCH_HEIGHT, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search tracks..."));
        searchBox.setResponder(this::onSearchChanged);
        searchBox.setMaxLength(100);
        addRenderableWidget(searchBox);
        y += SEARCH_HEIGHT + 4;

        // Track list
        int listHeight = imageHeight - SEARCH_HEIGHT - PLAYBACK_INFO_HEIGHT - SLIDER_HEIGHT - BUTTON_HEIGHT - PADDING * 5 - 16;
        trackList = new TrackListWidget(
            minecraft, contentWidth, listHeight, y, TRACK_ITEM_HEIGHT,
            this::onTrackSelected,
            this::onTrackDoubleClicked
        );
        trackList.setX(contentLeft);
        addRenderableWidget(trackList);
        y += listHeight + 4;

        // Playback info area is rendered manually (y reserved)
        y += PLAYBACK_INFO_HEIGHT + 4;

        // Volume slider
        volumeSlider = new VolumeSlider(contentLeft, y, contentWidth, SLIDER_HEIGHT, currentVolume, this::onVolumeChanged);
        addRenderableWidget(volumeSlider);
        y += SLIDER_HEIGHT + 4;

        // Control buttons
        int buttonWidth = (contentWidth - 8) / 2;

        playButton = Button.builder(Component.literal("Play"), btn -> playSelected())
            .bounds(contentLeft, y, buttonWidth, BUTTON_HEIGHT)
            .build();
        playButton.active = false;
        addRenderableWidget(playButton);

        stopButton = Button.builder(Component.literal("Stop"), btn -> stopPlayback())
            .bounds(contentLeft + buttonWidth + 8, y, buttonWidth, BUTTON_HEIGHT)
            .build();
        addRenderableWidget(stopButton);

        // Populate track list
        refreshTrackList();
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
        playButton.active = track != null;
    }

    private void onTrackDoubleClicked(AudioResource track) {
        selectedTrack = track;
        playSelected();
    }

    private void onVolumeChanged(float volume) {
        currentVolume = volume;
    }

    private void playSelected() {
        if (selectedTrack == null) return;
        PlatformHelper.INSTANCE.sendToServer(
            new SpeakerControlPacket(
                menu.getSpeakerPos(),
                SpeakerControlPacket.Action.PLAY,
                selectedTrack.id(),
                currentVolume
            )
        );
    }

    private void stopPlayback() {
        PlatformHelper.INSTANCE.sendToServer(
            new SpeakerControlPacket(
                menu.getSpeakerPos(),
                SpeakerControlPacket.Action.STOP,
                null,
                0
            )
        );
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // EditBox no longer needs tick() in 1.21
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Main background
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0101820);

        // Border
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 1, 0xFF2A3540);
        graphics.fill(leftPos, topPos + imageHeight - 1, leftPos + imageWidth, topPos + imageHeight, 0xFF2A3540);
        graphics.fill(leftPos, topPos, leftPos + 1, topPos + imageHeight, 0xFF2A3540);
        graphics.fill(leftPos + imageWidth - 1, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF2A3540);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int contentLeft = leftPos + PADDING;
        int contentWidth = imageWidth - PADDING * 2;

        // Title
        graphics.drawString(font, "Speaker Control", contentLeft, topPos + PADDING, 0xFFFFFF);

        // Track count (right-aligned in title area)
        List<AudioResource> allResources = ClientAudioManager.getInstance().getAllResources();
        String countText = allResources.size() + " tracks";
        int countWidth = font.width(countText);
        graphics.drawString(font, countText, leftPos + imageWidth - PADDING - countWidth, topPos + PADDING, 0x808080);

        // Playback info section
        renderPlaybackInfo(graphics, contentLeft, contentWidth);
    }

    private void renderPlaybackInfo(GuiGraphics graphics, int left, int width) {
        int y = topPos + PADDING + 12 + SEARCH_HEIGHT + 4;
        int listHeight = imageHeight - SEARCH_HEIGHT - PLAYBACK_INFO_HEIGHT - SLIDER_HEIGHT - BUTTON_HEIGHT - PADDING * 5 - 16;
        y += listHeight + 4;

        // Playback info background
        graphics.fill(left, y, left + width, y + PLAYBACK_INFO_HEIGHT, 0x40000000);

        Optional<PlaybackState> stateOpt = ClientSpeakerManager.getInstance().getSpeakerState(menu.getSpeakerPos());

        if (stateOpt.isPresent() && stateOpt.get().playing()) {
            PlaybackState state = stateOpt.get();
            UUID resourceId = state.resourceId();

            // Get resource info
            Optional<AudioResource> resourceOpt = ClientAudioManager.getInstance().getResource(resourceId);
            String trackName = resourceOpt.map(AudioResource::name).orElse("Unknown Track");
            long durationMs = resourceOpt.map(AudioResource::durationMs).orElse(-1L);

            // Now Playing label
            String nowPlaying = "Now Playing: " + trackName;
            if (font.width(nowPlaying) > width - 8) {
                nowPlaying = font.plainSubstrByWidth(nowPlaying, width - 13) + "...";
            }
            graphics.drawString(font, nowPlaying, left + 4, y + 4, 0x40FF40);

            // Progress bar
            long currentTime = System.currentTimeMillis();
            long positionMs = state.getCurrentPositionMs(currentTime);

            int barY = y + 16;
            int barWidth = width - 8;
            int barHeight = 4;

            // Background
            graphics.fill(left + 4, barY, left + 4 + barWidth, barY + barHeight, 0x40FFFFFF);

            // Progress
            if (durationMs > 0) {
                float progress = Math.min(1.0f, (float) positionMs / durationMs);
                int progressWidth = (int) (barWidth * progress);
                graphics.fill(left + 4, barY, left + 4 + progressWidth, barY + barHeight, 0xFF40FF40);
            }

            // Time text
            String timeText = formatTime(positionMs) + " / " + (durationMs > 0 ? formatTime(durationMs) : "--:--");
            graphics.drawString(font, timeText, left + 4, barY + 6, 0xA0A0A0);
        } else {
            graphics.drawString(font, "Not Playing", left + 4, y + 4, 0x808080);
            graphics.drawString(font, "Select a track and press Play", left + 4, y + 16, 0x606060);
        }
    }

    private String formatTime(long ms) {
        if (ms < 0) return "--:--";
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Disable default inventory labels
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Let search box capture keyboard input when focused
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
}
