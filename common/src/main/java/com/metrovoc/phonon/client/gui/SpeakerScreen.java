package com.metrovoc.phonon.client.gui;

import com.metrovoc.phonon.audio.AudioResource;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.block.SpeakerBlockEntity;
import com.metrovoc.phonon.client.ClientAudioManager;
import com.metrovoc.phonon.client.ClientSpeakerManager;
import com.metrovoc.phonon.menu.SpeakerMenu;
import com.metrovoc.phonon.client.ClientStations;
import com.metrovoc.phonon.platform.PlatformHelper;
import com.metrovoc.phonon.server.LibraryJob;
import com.metrovoc.phonon.server.SpeakerAction;
import com.metrovoc.phonon.station.Playlist;
import com.metrovoc.phonon.station.Station;
import com.metrovoc.phonon.station.StationView;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
    private static final int TAB_Y = 5;
    private static final int TAB_HEIGHT = 13;
    private static final int TAB_GAP = 8;
    private static final int FIELD_Y = 22;
    private static final int FIELD_HEIGHT = 20;
    private static final int FIELD_BUTTON_WIDTH = 20;
    private static final int FIELD_GAP = 2;
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
    private static final int TRANSPORT_GAP = 2;
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
    private static final String[] ICON_SHUFFLE = {
        "##.....##", "..#...#..", "...#.#...", "....#....", "...#.#...",
        "..#...#..", "##.....##", ".........", "........."
    };
    private static final String[] ICON_REPEAT = {
        ".#######.", "#.......#", "#.......#", "#.......#", "#.......#",
        "#.....#.#", "#....###.", ".######..", ".....#..."
    };
    private static final String[] ICON_REPEAT_ONE = {
        ".#######.", "#.......#", "#...#...#", "#..##...#", "#...#...#",
        "#...#.#.#", "#..###.#.", ".######..", ".....#..."
    };
    private static final String[] ICON_NOTE = {
        "...#####.", "...#...#.", "...#...#.", "...#...#.", "...#...#.",
        ".###.###.", "####.####", "####.####", ".##...##."
    };
    private static final String[] ICON_PLUS = {
        ".........", "....#....", "....#....", "....#....", ".#######.",
        "....#....", "....#....", "....#....", "........."
    };
    private static final String[] ICON_REMOVE = {
        ".........", ".#.....#.", "..#...#..", "...#.#...", "....#....",
        "...#.#...", "..#...#..", ".#.....#.", "........."
    };
    private static final String[] ICON_TRASH = {
        "...###...", ".#######.", ".........", ".#.#.#.#.", ".#.#.#.#.",
        ".#.#.#.#.", ".#.#.#.#.", ".#.#.#.#.", "..#####.."
    };
    private static final String[] ICON_BACK = {
        ".....##..", "....##...", "...##....", "..##.....", ".##......",
        "..##.....", "...##....", "....##...", ".....##.."
    };

    private static final Component PLAY = Component.translatable("gui.phonon.speaker.play");
    private static final Component PAUSE = Component.translatable("gui.phonon.speaker.pause");
    private static final Component STOP = Component.translatable("gui.phonon.speaker.stop");
    private static final Component PREVIOUS = Component.translatable("gui.phonon.speaker.previous");
    private static final Component NEXT = Component.translatable("gui.phonon.speaker.next");
    private static final Component SHUFFLE = Component.translatable("gui.phonon.speaker.shuffle");

    private enum Tab { TRACKS, QUEUE, PLAYLISTS, STATIONS }

    private final List<TabButton> tabs = new ArrayList<>();
    private EditBox field;
    private SteelButton fieldButton;
    private RowList list;
    private VolumeSlider volumeSlider;
    private ProgressSlider progressSlider;
    private SteelButton shuffleButton;
    private SteelButton previousButton;
    private SteelButton mainButton;
    private SteelButton nextButton;
    private SteelButton repeatButton;
    private SteelButton stopButton;

    private Tab tab = Tab.TRACKS;
    @Nullable private UUID selectedTrack;
    @Nullable private UUID openPlaylist;
    @Nullable private UUID confirmDelete;
    private boolean linkMode;
    /** A click finishes by focusing what was clicked, so the field takes focus after it. */
    private boolean focusFieldNextTick;
    private String search = "";
    private int listScroll = -1;
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

        tabs.clear();
        int tabX = leftPos + PADDING;
        for (Tab each : Tab.values()) {
            Component label = Component.translatable(switch (each) {
                case TRACKS -> "gui.phonon.speaker.tracks";
                case QUEUE -> "gui.phonon.speaker.queue";
                case PLAYLISTS -> "gui.phonon.speaker.playlists";
                case STATIONS -> "gui.phonon.speaker.stations";
            });
            int width = font.width(label);
            TabButton button = new TabButton(tabX, topPos + TAB_Y, width, TAB_HEIGHT, label, () -> openTab(each));
            tabs.add(button);
            addRenderableWidget(button);
            tabX += width + TAB_GAP;
        }

        field = new EditBox(font, leftPos + PADDING, topPos + FIELD_Y, LEFT_WIDTH, FIELD_HEIGHT,
            Component.translatable("gui.phonon.speaker.search"));
        field.setMaxLength(8192);
        field.setValue(search);
        field.setResponder(this::onFieldChanged);
        addRenderableWidget(field);

        fieldButton = new SteelButton(leftPos + PADDING + LEFT_WIDTH - FIELD_BUTTON_WIDTH, topPos + FIELD_Y,
            FIELD_BUTTON_WIDTH, FIELD_HEIGHT, Component.empty(), ICON_PLUS, this::toggleLinkMode);
        addRenderableWidget(fieldButton);

        list = new RowList(leftPos + PADDING, topPos + LIST_Y, LEFT_WIDTH, LIST_HEIGHT);
        addRenderableWidget(list);

        int x = leftPos + RIGHT_X;
        progressSlider = new ProgressSlider(x, topPos + PROGRESS_Y, RIGHT_WIDTH, PROGRESS_HEIGHT, this::onSeek);
        addRenderableWidget(progressSlider);

        volumeSlider = new VolumeSlider(x, topPos + VOLUME_Y, RIGHT_WIDTH, VOLUME_HEIGHT,
            ClientSpeakerManager.getInstance().getSpeakerVolume(pos()), this::onVolumeChanged, this::onVolumeCommit);
        addRenderableWidget(volumeSlider);

        int width = (RIGHT_WIDTH - TRANSPORT_GAP * 5) / 6;
        int y = topPos + TRANSPORT_Y;
        int step = width + TRANSPORT_GAP;
        shuffleButton = addTransport(x, y, width, SHUFFLE, ICON_SHUFFLE, () -> send(SpeakerAction.TOGGLE_SHUFFLE));
        previousButton = addTransport(x + step, y, width, PREVIOUS, ICON_PREVIOUS, () -> send(SpeakerAction.PREVIOUS));
        mainButton = addTransport(x + step * 2, y, width, PLAY, ICON_PLAY, this::pressMain);
        nextButton = addTransport(x + step * 3, y, width, NEXT, ICON_NEXT, () -> send(SpeakerAction.NEXT));
        repeatButton = addTransport(x + step * 4, y, width, PLAY, ICON_REPEAT, () -> send(SpeakerAction.CYCLE_REPEAT));
        stopButton = addTransport(x + step * 5, y, width, STOP, ICON_STOP, () -> send(SpeakerAction.STOP));

        if (selectedTrack == null) {
            selectedTrack = currentState().resourceId();
        }
        applyTab();
        refreshRows();
        if (listScroll >= 0) {
            list.setScroll(listScroll);
        } else {
            list.reveal(selectedTrack);
        }
        updateControls();
    }

    private SteelButton addTransport(int x, int y, int width, Component label, String[] icon, Runnable action) {
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
        return field;
    }

    private void send(SpeakerAction action) {
        PlatformHelper.INSTANCE.sendSpeakerAction(pos(), action, null, null, 0, "");
    }

    private void send(SpeakerAction action, @Nullable UUID target) {
        PlatformHelper.INSTANCE.sendSpeakerAction(pos(), action, target, null, 0, "");
    }

    private void send(SpeakerAction action, @Nullable UUID target, @Nullable UUID second) {
        PlatformHelper.INSTANCE.sendSpeakerAction(pos(), action, target, second, 0, "");
    }

    private void sendNumber(SpeakerAction action, int number) {
        PlatformHelper.INSTANCE.sendSpeakerAction(pos(), action, null, null, number, "");
    }

    private void sendText(SpeakerAction action, String text) {
        PlatformHelper.INSTANCE.sendSpeakerAction(pos(), action, null, null, 0, text);
    }

    private PlaybackState currentState() {
        return ClientSpeakerManager.getInstance().getSpeakerState(pos()).orElse(PlaybackState.STOPPED);
    }

    @Nullable
    private StationView station() {
        return ClientStations.getInstance().stationOf(pos());
    }

    private static Optional<AudioResource> track(@Nullable UUID id) {
        return id == null ? Optional.empty() : ClientAudioManager.getInstance().getResource(id);
    }

    private void openTab(Tab next) {
        tab = next;
        linkMode = false;
        openPlaylist = null;
        confirmDelete = null;
        search = "";
        field.setValue("");
        list.setScroll(0);
        applyTab();
        refreshRows();
    }

    /** Shows the text field and its button the way the open tab uses them. */
    private void applyTab() {
        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).setOpen(tab.ordinal() == i);
        }
        boolean canAddTracks = tab == Tab.TRACKS && GuiPort.isOperator();
        field.visible = tab == Tab.TRACKS || tab == Tab.STATIONS || (tab == Tab.PLAYLISTS && openPlaylist == null);
        fieldButton.visible = canAddTracks;
        fieldButton.show(Component.translatable(linkMode ? "gui.phonon.speaker.back_to_search" : "gui.phonon.speaker.add_link"),
            linkMode ? ICON_BACK : ICON_PLUS);
        field.setWidth(canAddTracks ? LEFT_WIDTH - FIELD_BUTTON_WIDTH - FIELD_GAP : LEFT_WIDTH);
        field.setHint(Component.translatable(switch (tab) {
            case TRACKS -> linkMode ? "gui.phonon.speaker.hint_link" : "gui.phonon.speaker.search_hint";
            case PLAYLISTS -> "gui.phonon.speaker.hint_playlist";
            case STATIONS -> "gui.phonon.speaker.hint_station";
            case QUEUE -> "gui.phonon.speaker.search_hint";
        }));
    }

    private void toggleLinkMode() {
        linkMode = !linkMode;
        search = "";
        field.setValue("");
        applyTab();
        refreshRows();
        focusFieldNextTick = true;
    }

    private void onFieldChanged(String value) {
        search = value;
        refreshRows();
    }

    @Override
    void submitField() {
        String text = field.getValue().strip();
        if (text.isEmpty()) {
            return;
        }
        if (tab == Tab.TRACKS && linkMode) {
            sendText(SpeakerAction.ADD_LINK, text);
        } else if (tab == Tab.PLAYLISTS) {
            sendText(SpeakerAction.CREATE_PLAYLIST, text);
        } else if (tab == Tab.STATIONS) {
            sendText(SpeakerAction.CREATE_STATION, text);
        } else {
            return;
        }
        search = "";
        field.setValue("");
    }

    private List<FormattedCharSequence> wrapped(String key) {
        return font.split(Component.translatable(key), LEFT_WIDTH - 16);
    }

    private void refreshRows() {
        switch (tab) {
            case TRACKS -> list.setRows(trackRows(), wrapped(!search.isEmpty() && !linkMode
                ? "gui.phonon.speaker.no_match"
                : GuiPort.isOperator() ? "gui.phonon.speaker.empty_operator" : "gui.phonon.speaker.empty_player"));
            case QUEUE -> list.setRows(queueRows(), wrapped("gui.phonon.speaker.queue_empty"));
            case PLAYLISTS -> {
                Playlist open = playlist(openPlaylist);
                if (open != null) {
                    list.setRows(playlistTrackRows(open), wrapped("gui.phonon.speaker.playlist_empty"));
                } else {
                    openPlaylist = null;
                    list.setRows(playlistRows(), wrapped("gui.phonon.speaker.playlists_empty"));
                }
            }
            case STATIONS -> list.setRows(stationRows(), List.of());
        }
    }

    @Nullable
    private static Playlist playlist(@Nullable UUID id) {
        return ClientStations.getInstance().playlists().stream().filter(each -> each.id().equals(id)).findFirst().orElse(null);
    }

    private boolean mayManage(@Nullable UUID owner) {
        return GuiPort.isOperator() || (minecraft.player != null && minecraft.player.getUUID().equals(owner));
    }

    private static RowList.Row plainRow(
        @Nullable UUID key, String text, String detail, int color, boolean selected,
        @Nullable String[] marker, int markerColor, @Nullable UUID head,
        List<RowList.Action> actions, @Nullable Runnable click, @Nullable Runnable doubleClick
    ) {
        return new RowList.Row(key, text, detail, color, selected, marker, markerColor, head, -1, actions, click, doubleClick);
    }

    private static Component label(String key) {
        return Component.translatable(key);
    }

    private List<RowList.Row> trackRows() {
        List<RowList.Row> rows = new ArrayList<>();
        for (LibraryJob job : ClientStations.getInstance().jobs()) {
            rows.add(new RowList.Row(null, job.label(),
                job.percent() >= 0 ? job.percent() + "%" : label("gui.phonon.speaker.adding").getString(),
                SteelStyle.TEXT_MUTED, false, null, 0, null, Math.max(0, job.percent()), List.of(), null, null));
        }
        List<AudioResource> resources = search.isEmpty() || linkMode
            ? ClientAudioManager.getInstance().getAllResources()
            : ClientAudioManager.getInstance().searchResources(search);
        PlaybackState state = currentState();
        for (AudioResource resource : resources) {
            boolean loaded = resource.id().equals(state.resourceId());
            int color = !loaded ? SteelStyle.TEXT_DIM : state.isPaused() ? SteelStyle.PAUSED : SteelStyle.PLAYING;
            List<RowList.Action> actions = new ArrayList<>();
            if (GuiPort.isOperator()) {
                boolean confirming = resource.id().equals(confirmDelete);
                actions.add(new RowList.Action(ICON_TRASH,
                    label(confirming ? "gui.phonon.speaker.action_confirm" : "gui.phonon.speaker.action_delete"),
                    () -> {
                        if (resource.id().equals(confirmDelete)) {
                            confirmDelete = null;
                            send(SpeakerAction.DELETE_TRACK, resource.id());
                        } else {
                            confirmDelete = resource.id();
                        }
                    }));
            }
            actions.add(new RowList.Action(ICON_PLUS, label("gui.phonon.speaker.action_play_next"),
                () -> send(SpeakerAction.PLAY_NEXT, resource.id())));
            rows.add(plainRow(resource.id(), resource.name(), SteelStyle.time(resource.durationMs()),
                loaded ? color : resource.id().equals(selectedTrack) ? SteelStyle.TEXT : SteelStyle.TEXT_DIM,
                resource.id().equals(selectedTrack), loaded ? ICON_NOTE : null, color, null, actions,
                () -> selectTrack(resource.id()),
                () -> send(SpeakerAction.PLAY_TRACK, resource.id())));
        }
        return rows;
    }

    private List<RowList.Row> queueRows() {
        List<RowList.Row> rows = new ArrayList<>();
        StationView view = station();
        if (view == null) {
            return rows;
        }
        PlaybackState state = currentState();
        for (int i = 0; i < view.queue().size(); i++) {
            int index = i;
            UUID id = view.queue().get(i);
            boolean current = i == view.cursor() && !state.isStopped();
            int color = !current ? SteelStyle.TEXT_DIM : state.isPaused() ? SteelStyle.PAUSED : SteelStyle.PLAYING;
            Optional<AudioResource> resource = track(id);
            rows.add(plainRow(id, resource.map(AudioResource::name).orElse("?"),
                SteelStyle.time(resource.map(AudioResource::durationMs).orElse(-1L)), color, id.equals(selectedTrack),
                current ? ICON_NOTE : null, color, null,
                List.of(new RowList.Action(ICON_REMOVE, label("gui.phonon.speaker.action_remove"),
                    () -> sendNumber(SpeakerAction.REMOVE_FROM_QUEUE, index))),
                () -> selectTrack(id),
                () -> sendNumber(SpeakerAction.JUMP_TO, index)));
        }
        return rows;
    }

    private List<RowList.Row> playlistRows() {
        List<RowList.Row> rows = new ArrayList<>();
        for (Playlist playlist : ClientStations.getInstance().playlists()) {
            List<RowList.Action> actions = new ArrayList<>();
            if (mayManage(playlist.ownerId())) {
                actions.add(new RowList.Action(ICON_TRASH, label("gui.phonon.speaker.action_delete_playlist"),
                    () -> send(SpeakerAction.DELETE_PLAYLIST, playlist.id())));
                actions.add(new RowList.Action(ICON_PLUS, label("gui.phonon.speaker.action_add_selected"),
                    () -> send(SpeakerAction.ADD_TO_PLAYLIST, playlist.id(), selectedTrack)));
            }
            actions.add(new RowList.Action(ICON_PLAY, label("gui.phonon.speaker.action_play"),
                () -> send(SpeakerAction.PLAY_PLAYLIST, playlist.id())));
            rows.add(plainRow(playlist.id(), playlist.name(), Component.translatable("gui.phonon.speaker.track_count", playlist.tracks().size()).getString(),
                SteelStyle.TEXT_DIM, false, null, 0, playlist.ownerId(), actions,
                () -> {
                    openPlaylist = playlist.id();
                    list.setScroll(0);
                    applyTab();
                    refreshRows();
                }, null));
        }
        return rows;
    }

    private List<RowList.Row> playlistTrackRows(Playlist playlist) {
        List<RowList.Row> rows = new ArrayList<>();
        rows.add(plainRow(null, label("gui.phonon.speaker.back").getString(),
            Component.translatable("gui.phonon.speaker.by", playlist.ownerName()).getString(),
            SteelStyle.TEXT, false, ICON_BACK, SteelStyle.TEXT, null,
            List.of(new RowList.Action(ICON_PLAY, label("gui.phonon.speaker.action_play"),
                () -> send(SpeakerAction.PLAY_PLAYLIST, playlist.id()))),
            () -> {
                openPlaylist = null;
                list.setScroll(0);
                applyTab();
                refreshRows();
            }, null));
        boolean manage = mayManage(playlist.ownerId());
        for (UUID id : playlist.tracks()) {
            Optional<AudioResource> resource = track(id);
            rows.add(plainRow(id, resource.map(AudioResource::name).orElse("?"),
                SteelStyle.time(resource.map(AudioResource::durationMs).orElse(-1L)), SteelStyle.TEXT_DIM,
                id.equals(selectedTrack), null, 0, null,
                manage ? List.of(new RowList.Action(ICON_REMOVE, label("gui.phonon.speaker.action_remove"),
                    () -> send(SpeakerAction.REMOVE_FROM_PLAYLIST, playlist.id(), id))) : List.of(),
                () -> selectTrack(id), null));
        }
        return rows;
    }

    private List<RowList.Row> stationRows() {
        List<RowList.Row> rows = new ArrayList<>();
        StationView tuned = station();
        boolean onOwn = tuned == null || !tuned.shared();
        rows.add(plainRow(null, label("gui.phonon.speaker.this_speaker").getString(), "",
            onOwn ? SteelStyle.PLAYING : SteelStyle.TEXT_DIM, onOwn, onOwn ? ICON_NOTE : null, SteelStyle.PLAYING, null,
            List.of(), () -> send(SpeakerAction.TUNE, null), null));
        for (StationView view : ClientStations.getInstance().shared()) {
            boolean on = tuned != null && view.id().equals(tuned.id());
            List<RowList.Action> actions = mayManage(view.ownerId())
                ? List.of(new RowList.Action(ICON_TRASH, label("gui.phonon.speaker.action_delete_station"),
                    () -> send(SpeakerAction.DELETE_STATION, view.id())))
                : List.of();
            rows.add(plainRow(view.id(), view.name(), view.ownerName(),
                on ? SteelStyle.PLAYING : SteelStyle.TEXT_DIM, on, null, 0, view.ownerId(), actions,
                () -> send(SpeakerAction.TUNE, view.id()), null));
        }
        return rows;
    }

    private void selectTrack(UUID id) {
        selectedTrack = id;
        confirmDelete = null;
        refreshRows();
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
            send(SpeakerAction.PAUSE);
        } else if (state.isPaused() && selectedIsLoaded) {
            send(SpeakerAction.RESUME);
        } else if (selectedTrack != null) {
            send(SpeakerAction.PLAY_TRACK, selectedTrack);
        }
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
        if (focusFieldNextTick) {
            focusFieldNextTick = false;
            setFocused(field);
        }
        listScroll = list.scroll();
        refreshRows();
        updateControls();
    }

    private void updateControls() {
        PlaybackState state = currentState();
        boolean loaded = !state.isStopped();
        StationView view = station();

        progressSlider.update(loaded ? state.getCurrentPositionMs() : 0,
            loaded ? track(state.resourceId()).map(AudioResource::durationMs).orElse(-1L) : -1);
        volumeSlider.follow(ClientSpeakerManager.getInstance().getSpeakerVolume(pos()));

        boolean selectedIsLoaded = selectedTrack == null || selectedTrack.equals(state.resourceId());
        boolean pausable = state.isPlaying() && selectedIsLoaded;
        mainButton.show(pausable ? PAUSE : PLAY, pausable ? ICON_PAUSE : ICON_PLAY);
        mainButton.active = pausable || (state.isPaused() && selectedIsLoaded) || selectedTrack != null;
        stopButton.active = loaded;
        boolean anything = !ClientAudioManager.getInstance().getAllResources().isEmpty();
        previousButton.active = anything;
        nextButton.active = anything;

        Station.Repeat repeat = view == null ? Station.Repeat.OFF : view.repeat();
        repeatButton.show(Component.translatable(switch (repeat) {
            case OFF -> "gui.phonon.speaker.repeat_off";
            case ALL -> "gui.phonon.speaker.repeat_all";
            case ONE -> "gui.phonon.speaker.repeat_one";
        }), repeat == Station.Repeat.ONE ? ICON_REPEAT_ONE : ICON_REPEAT);
        repeatButton.setLit(repeat != Station.Repeat.OFF);
        shuffleButton.setLit(view != null && view.shuffle());
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

        if (tab == Tab.QUEUE || (tab == Tab.PLAYLISTS && openPlaylist != null)) {
            drawListHeading(canvas, leftX);
        }
        canvas.text(Component.translatable("gui.phonon.speaker.now_playing"), rightX, topPos + LABEL_Y, SteelStyle.TEXT);
        StationView view = station();
        if (view != null && view.shared()) {
            String name = view.name();
            int room = RIGHT_WIDTH - canvas.font.width(Component.translatable("gui.phonon.speaker.now_playing")) - 6;
            if (canvas.font.width(name) > room) {
                name = canvas.font.plainSubstrByWidth(name, Math.max(0, room - canvas.font.width("..."))) + "...";
            }
            canvas.text(name, rightX + RIGHT_WIDTH - canvas.font.width(name), topPos + LABEL_Y, SteelStyle.PLAYING);
        }
        drawNowPlaying(canvas, rightX, mouseX, mouseY);
        drawMeter(canvas, rightX);
        drawSelected(canvas, rightX);
        drawButtonTooltip(canvas, mouseX, mouseY);
    }

    private void drawListHeading(GuiPort.Canvas canvas, int x) {
        int y = topPos + FIELD_Y + 6;
        if (tab == Tab.QUEUE) {
            StationView view = station();
            canvas.text(Component.translatable("gui.phonon.speaker.track_count", view == null ? 0 : view.queue().size()), x, y, SteelStyle.TEXT_MUTED);
        } else {
            Playlist open = playlist(openPlaylist);
            if (open != null) {
                canvas.head(open.ownerId(), x, y - 1, 8);
                canvas.text(open.name(), x + 12, y, SteelStyle.TEXT);
            }
        }
    }

    private void drawNowPlaying(GuiPort.Canvas canvas, int x, int mouseX, int mouseY) {
        PlaybackState state = currentState();
        int y = topPos + TITLE_Y;
        if (state.isStopped()) {
            canvas.text(Component.translatable("gui.phonon.speaker.nothing_playing"), x, y, SteelStyle.TEXT_OFF);
            return;
        }

        Optional<AudioResource> loaded = track(state.resourceId());
        String title = loaded.map(AudioResource::name)
            .orElseGet(() -> Component.translatable("gui.phonon.speaker.unknown").getString());
        String shown = title;
        boolean cut = canvas.font.width(shown) > RIGHT_WIDTH;
        if (cut) {
            shown = canvas.font.plainSubstrByWidth(shown, RIGHT_WIDTH - canvas.font.width("...")) + "...";
        }
        canvas.text(shown, x, y, state.isPaused() ? SteelStyle.PAUSED : SteelStyle.PLAYING);

        Component status = status(state, loaded.isPresent());
        if (status != null) {
            canvas.text(status, x, topPos + STATUS_Y, SteelStyle.TEXT_MUTED);
        }
        if (cut && mouseX >= x && mouseX < x + RIGHT_WIDTH && mouseY >= y && mouseY < y + canvas.font.lineHeight) {
            canvas.tooltip(Component.literal(title), mouseX, mouseY);
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
            for (FormattedCharSequence line : canvas.font.split(Component.translatable("gui.phonon.speaker.select_hint"), RIGHT_WIDTH)) {
                canvas.line(line, x, y, SteelStyle.TEXT_OFF);
                y += canvas.font.lineHeight + 1;
            }
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
        List<SteelButton> buttons = List.of(shuffleButton, previousButton, mainButton, nextButton, repeatButton, stopButton, fieldButton);
        for (SteelButton button : buttons) {
            if (button.visible && button.isMouseOver(mouseX, mouseY)) {
                canvas.tooltip(button.getMessage(), mouseX, mouseY);
            }
        }
    }
}
