package com.metrovoc.phonon.client.gui;

import com.metrovoc.phonon.audio.AudioResource;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.block.SpeakerBlockEntity;
import com.metrovoc.phonon.client.ClientAudioManager;
import com.metrovoc.phonon.client.ClientSpeakerManager;
import com.metrovoc.phonon.menu.SpeakerMenu;
import com.metrovoc.phonon.platform.PlatformHelper;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Speaker 控制 GUI。
 * 左栏: 搜索 + 曲目列表
 * 右栏: 当前播放 + 进度条 + 音量 + 控制按钮 + 选中曲目信息
 */
public class SpeakerScreen extends GuiPort.PanelScreen {
    private static final int WIDTH = 360;
    private static final int HEIGHT = 240;
    private static final int PADDING = 8;
    private static final int LEFT_WIDTH = 184;
    private static final int RIGHT_X = 200;
    private static final int RIGHT_WIDTH = 152;
    private static final int DIVIDER_X = 196;
    private static final int LABEL_Y = 8;
    private static final int SEARCH_Y = 22;
    private static final int SEARCH_HEIGHT = 20;
    private static final int LIST_Y = 46;
    private static final int LIST_HEIGHT = 186;
    private static final int TITLE_Y = 22;
    private static final int STATUS_Y = 33;
    private static final int PROGRESS_Y = 46;
    private static final int PROGRESS_HEIGHT = 24;
    private static final int VOLUME_Y = 78;
    private static final int VOLUME_HEIGHT = 20;
    private static final int TRANSPORT_Y = 106;
    private static final int TRANSPORT_HEIGHT = 20;
    private static final int TRANSPORT_GAP = 4;
    private static final int METER_Y = 131;
    private static final int METER_HEIGHT = 14;
    private static final int METER_BARS = 38;
    private static final int METER_BAR_WIDTH = 3;
    private static final int METER_BAR_MS = 100;
    private static final int SELECTED_Y = 151;
    private static final int MAX_SELECTED_LINES = 4;
    private static final double MAX_USE_DISTANCE_SQUARED = 64.0;

    private static final String[] ICON_PLAY = {
        "##.......", "####.....", "######...", "########.", "#########",
        "########.", "######...", "####.....", "##......."
    };
    private static final String[] ICON_PAUSE = {
        "###...###", "###...###", "###...###", "###...###", "###...###",
        "###...###", "###...###", "###...###", "###...###"
    };
    private static final String[] ICON_STOP = {
        ".........", ".#######.", ".#######.", ".#######.", ".#######.",
        ".#######.", ".#######.", ".#######.", "........."
    };
    private static final String[] ICON_PREVIOUS = {
        "#.......#", "#.....###", "#...#####", "#.#######", "#########",
        "#.#######", "#...#####", "#.....###", "#.......#"
    };
    private static final String[] ICON_NEXT = {
        "#.......#", "###.....#", "#####...#", "#######.#", "#########",
        "#######.#", "#####...#", "###.....#", "#.......#"
    };

    private static final Component PLAY = Component.translatable("gui.phonon.speaker.play");
    private static final Component PAUSE = Component.translatable("gui.phonon.speaker.pause");
    private static final Component STOP = Component.translatable("gui.phonon.speaker.stop");
    private static final Component PREVIOUS = Component.translatable("gui.phonon.speaker.previous");
    private static final Component NEXT = Component.translatable("gui.phonon.speaker.next");

    private EditBox searchBox;
    private TrackList trackList;
    private VolumeSlider volumeSlider;
    private ProgressSlider progressSlider;
    private SteelButton previousButton;
    private SteelButton mainButton;
    private SteelButton nextButton;
    private SteelButton stopButton;

    @Nullable
    private UUID selectedTrack;
    private String search = "";
    private int listScroll = -1;
    private String titleShown = "";
    private boolean titleCut;
    private final float[] meter = new float[METER_BARS];

    public SpeakerScreen(SpeakerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, WIDTH, HEIGHT);
    }

    private BlockPos pos() {
        return menu.getSpeakerPos();
    }

    @Override
    protected void init() {
        super.init();
        ClientSpeakerManager.getInstance().holdNowPlaying();

        searchBox = new EditBox(font, leftPos + PADDING, topPos + SEARCH_Y, LEFT_WIDTH, SEARCH_HEIGHT,
            Component.translatable("gui.phonon.speaker.search"));
        searchBox.setHint(Component.translatable("gui.phonon.speaker.search_hint"));
        searchBox.setMaxLength(100);
        searchBox.setValue(search);
        searchBox.setResponder(this::onSearchChanged);
        addRenderableWidget(searchBox);

        trackList = new TrackList(leftPos + PADDING, topPos + LIST_Y, LEFT_WIDTH, LIST_HEIGHT,
            track -> selectTrack(track.id()),
            track -> playTrack(track.id()));
        addRenderableWidget(trackList);

        int x = leftPos + RIGHT_X;
        progressSlider = new ProgressSlider(x, topPos + PROGRESS_Y, RIGHT_WIDTH, PROGRESS_HEIGHT, this::onSeek);
        addRenderableWidget(progressSlider);

        volumeSlider = new VolumeSlider(x, topPos + VOLUME_Y, RIGHT_WIDTH, VOLUME_HEIGHT,
            ClientSpeakerManager.getInstance().getSpeakerVolume(pos()), this::onVolumeChanged, this::onVolumeCommit);
        addRenderableWidget(volumeSlider);

        int buttonWidth = (RIGHT_WIDTH - TRANSPORT_GAP * 3) / 4;
        int y = topPos + TRANSPORT_Y;
        previousButton = addButton(x, y, buttonWidth, PREVIOUS, ICON_PREVIOUS, () -> playNeighbor(-1));
        mainButton = addButton(x + (buttonWidth + TRANSPORT_GAP), y, buttonWidth, PLAY, ICON_PLAY, this::pressMain);
        nextButton = addButton(x + (buttonWidth + TRANSPORT_GAP) * 2, y, buttonWidth, NEXT, ICON_NEXT, () -> playNeighbor(1));
        stopButton = addButton(x + (buttonWidth + TRANSPORT_GAP) * 3, y, buttonWidth, STOP, ICON_STOP, this::stopPlayback);

        refreshTrackList();
        PlaybackState state = currentState();
        if (selectedTrack == null) {
            selectedTrack = state.resourceId();
        }
        trackList.setSelected(selectedTrack);
        if (listScroll >= 0) {
            trackList.setScroll(listScroll);
        } else {
            trackList.reveal(selectedTrack);
        }
        updateControls();
    }

    private SteelButton addButton(int x, int y, int width, Component label, String[] icon, Runnable action) {
        SteelButton button = new SteelButton(x, y, width, TRANSPORT_HEIGHT, label, icon, action);
        addRenderableWidget(button);
        return button;
    }

    @Override
    public void removed() {
        super.removed();
        ClientSpeakerManager.getInstance().releaseNowPlaying();
    }

    @Override
    EditBox textField() {
        return searchBox;
    }

    private PlaybackState currentState() {
        return ClientSpeakerManager.getInstance().getSpeakerState(pos()).orElse(PlaybackState.STOPPED);
    }

    private static Optional<AudioResource> track(@Nullable UUID id) {
        return id == null ? Optional.empty() : ClientAudioManager.getInstance().getResource(id);
    }

    private void refreshTrackList() {
        List<AudioResource> tracks = search.isEmpty()
            ? ClientAudioManager.getInstance().getAllResources()
            : ClientAudioManager.getInstance().searchResources(search);
        trackList.setTracks(tracks);
        trackList.setEmptyMessage(font.split(emptyMessage(), LEFT_WIDTH - 16));
    }

    private Component emptyMessage() {
        if (!search.isEmpty()) {
            return Component.translatable("gui.phonon.speaker.no_match");
        }
        return Component.translatable(GuiPort.isOperator()
            ? "gui.phonon.speaker.empty_operator"
            : "gui.phonon.speaker.empty_player");
    }

    private void onSearchChanged(String query) {
        search = query;
        refreshTrackList();
    }

    private void selectTrack(UUID id) {
        selectedTrack = id;
        trackList.setSelected(id);
        updateControls();
    }

    private void onVolumeChanged(float volume) {
        ClientSpeakerManager.getInstance().updateVolume(pos(), volume);
    }

    private void onVolumeCommit() {
        PlatformHelper.INSTANCE.setSpeakerVolume(pos(), ClientSpeakerManager.getInstance().getSpeakerVolume(pos()));
    }

    private void onSeek(float progress) {
        PlaybackState state = currentState();
        long durationMs = track(state.resourceId()).map(AudioResource::durationMs).orElse(-1L);
        if (!state.isStopped() && durationMs > 0) {
            PlatformHelper.INSTANCE.seekSpeaker(pos(), (long) (progress * durationMs));
        }
    }

    /** Plays the selected track, or pauses and resumes when it's the one already loaded. */
    private void pressMain() {
        PlaybackState state = currentState();
        boolean selectedIsLoaded = selectedTrack == null || selectedTrack.equals(state.resourceId());
        if (state.isPlaying() && selectedIsLoaded) {
            PlatformHelper.INSTANCE.pauseSpeaker(pos(), state.resourceId());
        } else if (state.isPaused() && selectedIsLoaded) {
            PlatformHelper.INSTANCE.playOnSpeaker(pos(), state.resourceId());
        } else if (selectedTrack != null) {
            playTrack(selectedTrack);
        }
    }

    private void playTrack(UUID id) {
        PlaybackState state = currentState();
        if (state.isPaused() && id.equals(state.resourceId())) {
            PlatformHelper.INSTANCE.stopSpeaker(pos());
        }
        PlatformHelper.INSTANCE.playOnSpeaker(pos(), id);
    }

    private void playNeighbor(int direction) {
        UUID from = currentState().isStopped() ? selectedTrack : currentState().resourceId();
        AudioResource next = trackList.neighbor(from, direction);
        if (next != null) {
            selectTrack(next.id());
            trackList.reveal(next.id());
            playTrack(next.id());
        }
    }

    private void stopPlayback() {
        PlatformHelper.INSTANCE.stopSpeaker(pos());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (minecraft.player == null || minecraft.level == null
            || !(minecraft.level.getBlockEntity(pos()) instanceof SpeakerBlockEntity)
            || minecraft.player.distanceToSqr(pos().getX() + 0.5, pos().getY() + 0.5, pos().getZ() + 0.5)
                > MAX_USE_DISTANCE_SQUARED) {
            onClose();
            return;
        }
        listScroll = trackList.scroll();
        refreshTrackList();
        updateControls();
    }

    private void updateControls() {
        PlaybackState state = currentState();
        boolean loaded = !state.isStopped();

        trackList.setPlaying(state.resourceId(), state.isPaused() ? SteelStyle.PAUSED : SteelStyle.PLAYING);
        progressSlider.update(loaded ? state.getCurrentPositionMs() : 0,
            loaded ? track(state.resourceId()).map(AudioResource::durationMs).orElse(-1L) : -1);
        volumeSlider.follow(ClientSpeakerManager.getInstance().getSpeakerVolume(pos()));

        boolean selectedIsLoaded = selectedTrack == null || selectedTrack.equals(state.resourceId());
        boolean pausable = state.isPlaying() && selectedIsLoaded;
        mainButton.show(pausable ? PAUSE : PLAY, pausable ? ICON_PAUSE : ICON_PLAY);
        mainButton.active = pausable || (state.isPaused() && selectedIsLoaded) || selectedTrack != null;
        stopButton.active = loaded;
        previousButton.active = !trackList.isEmpty();
        nextButton.active = !trackList.isEmpty();
    }

    @Override
    void drawPanel(GuiPort.Canvas canvas) {
        int right = leftPos + WIDTH;
        int bottom = topPos + HEIGHT;
        canvas.fill(leftPos, topPos, right, bottom, SteelStyle.PANEL);
        canvas.fill(leftPos, topPos, right, topPos + 1, SteelStyle.PANEL_EDGE);
        canvas.fill(leftPos, bottom - 1, right, bottom, SteelStyle.PANEL_EDGE);
        canvas.fill(leftPos, topPos, leftPos + 1, bottom, SteelStyle.PANEL_EDGE);
        canvas.fill(right - 1, topPos, right, bottom, SteelStyle.PANEL_EDGE);
        int divider = leftPos + DIVIDER_X;
        canvas.fill(divider, topPos + PADDING, divider + 1, bottom - PADDING, SteelStyle.DIVIDER);
    }

    @Override
    void drawOverlay(GuiPort.Canvas canvas, int mouseX, int mouseY) {
        int leftX = leftPos + PADDING;
        int rightX = leftPos + RIGHT_X;

        canvas.text(Component.translatable("gui.phonon.speaker.tracks"), leftX, topPos + LABEL_Y, SteelStyle.TEXT);
        Component count = Component.translatable("gui.phonon.speaker.total",
            ClientAudioManager.getInstance().getAllResources().size());
        canvas.text(count, leftX + LEFT_WIDTH - canvas.font.width(count), topPos + LABEL_Y, SteelStyle.TEXT_MUTED);

        canvas.text(Component.translatable("gui.phonon.speaker.now_playing"), rightX, topPos + LABEL_Y, SteelStyle.TEXT);
        drawNowPlaying(canvas, rightX, mouseX, mouseY);
        drawMeter(canvas, rightX);
        drawSelected(canvas, rightX);
        drawButtonTooltip(canvas, mouseX, mouseY);
    }

    private void drawNowPlaying(GuiPort.Canvas canvas, int x, int mouseX, int mouseY) {
        PlaybackState state = currentState();
        int y = topPos + TITLE_Y;
        titleCut = false;
        if (state.isStopped()) {
            canvas.text(Component.translatable("gui.phonon.speaker.nothing_playing"), x, y, SteelStyle.TEXT_OFF);
            return;
        }

        Optional<AudioResource> loaded = track(state.resourceId());
        titleShown = loaded.map(AudioResource::name)
            .orElseGet(() -> Component.translatable("gui.phonon.speaker.unknown").getString());
        String shown = titleShown;
        if (canvas.font.width(shown) > RIGHT_WIDTH) {
            shown = canvas.font.plainSubstrByWidth(shown, RIGHT_WIDTH - canvas.font.width("...")) + "...";
            titleCut = true;
        }
        canvas.text(shown, x, y, state.isPaused() ? SteelStyle.PAUSED : SteelStyle.PLAYING);

        Component status = status(state, loaded.isPresent());
        if (status != null) {
            canvas.text(status, x, topPos + STATUS_Y, SteelStyle.TEXT_MUTED);
        }
        if (titleCut && mouseX >= x && mouseX < x + RIGHT_WIDTH && mouseY >= y && mouseY < y + canvas.font.lineHeight) {
            canvas.tooltip(Component.literal(titleShown), mouseX, mouseY);
        }
    }

    @Nullable
    private Component status(PlaybackState state, boolean available) {
        if (!available || ClientSpeakerManager.getInstance().hasFailed(pos())) {
            return Component.translatable("gui.phonon.speaker.unavailable");
        }
        if (state.isPaused()) {
            return Component.translatable("gui.phonon.speaker.paused");
        }
        return ClientSpeakerManager.getInstance().isBuffering(pos())
            ? Component.translatable("gui.phonon.speaker.buffering")
            : null;
    }

    /** Recent loudness of what this speaker is playing, newest on the right. */
    private void drawMeter(GuiPort.Canvas canvas, int x) {
        PlaybackState state = currentState();
        int bottom = topPos + METER_Y + METER_HEIGHT;
        canvas.fill(x, topPos + METER_Y, x + RIGHT_WIDTH, bottom, SteelStyle.WELL);
        if (state.isPlaying()) {
            PlatformHelper.INSTANCE.readAudioLevels(pos(), state.getCurrentPositionMs(), meter, METER_BAR_MS);
        } else {
            java.util.Arrays.fill(meter, 0);
        }
        for (int i = 0; i < METER_BARS; i++) {
            int height = Math.round(meter[i] * (METER_HEIGHT - 2));
            int left = x + 1 + i * (METER_BAR_WIDTH + 1);
            canvas.fill(left, bottom - 1 - height, left + METER_BAR_WIDTH, bottom - 1, SteelStyle.PLAYING);
        }
    }

    private void drawSelected(GuiPort.Canvas canvas, int x) {
        int y = topPos + SELECTED_Y;
        canvas.fill(x, y, x + RIGHT_WIDTH, y + 1, SteelStyle.DIVIDER);
        y += 6;
        canvas.text(Component.translatable("gui.phonon.speaker.selected"), x, y, SteelStyle.TEXT_MUTED);
        y += canvas.font.lineHeight + 3;

        Optional<AudioResource> selected = track(selectedTrack);
        if (selected.isEmpty()) {
            canvas.text(Component.translatable("gui.phonon.speaker.select_hint"), x, y, SteelStyle.TEXT_OFF);
            return;
        }

        List<FormattedCharSequence> lines = canvas.font.split(Component.literal(selected.get().name()), RIGHT_WIDTH);
        for (int i = 0; i < Math.min(lines.size(), MAX_SELECTED_LINES); i++) {
            canvas.line(lines.get(i), x, y, SteelStyle.TEXT);
            y += canvas.font.lineHeight + 1;
        }
        y += 3;
        canvas.text(Component.translatable("gui.phonon.speaker.selected_details",
            SteelStyle.time(selected.get().durationMs()), size(selected.get().sizeBytes())), x, y, SteelStyle.TEXT_DIM);
    }

    private static String size(long bytes) {
        if (bytes <= 0) return "? KB";
        if (bytes < 1024 * 1024) return String.format("%.0f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void drawButtonTooltip(GuiPort.Canvas canvas, int mouseX, int mouseY) {
        for (SteelButton button : List.of(previousButton, mainButton, nextButton, stopButton)) {
            if (button.isMouseOver(mouseX, mouseY)) {
                canvas.tooltip(button.getMessage(), mouseX, mouseY);
            }
        }
    }
}
