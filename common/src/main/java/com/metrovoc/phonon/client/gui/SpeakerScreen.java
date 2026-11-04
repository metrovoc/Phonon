package com.metrovoc.phonon.client.gui;

import com.metrovoc.phonon.audio.AudioResource;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.audio.Receiver;
import com.metrovoc.phonon.block.SpeakerBlockEntity;
import com.metrovoc.phonon.client.ClientAudioManager;
import com.metrovoc.phonon.client.ClientSpeakerManager;
import com.metrovoc.phonon.client.ClientStations;
import com.metrovoc.phonon.item.BoomboxItem;
import com.metrovoc.phonon.menu.SpeakerMenu;
import com.metrovoc.phonon.platform.PlatformHelper;
import com.metrovoc.phonon.server.AddRequest;
import com.metrovoc.phonon.server.AudioFailure;
import com.metrovoc.phonon.server.LibraryJob;
import com.metrovoc.phonon.server.SearchResult;
import com.metrovoc.phonon.server.SearchSource;
import com.metrovoc.phonon.server.SpeakerAction;
import com.metrovoc.phonon.station.Playlist;
import com.metrovoc.phonon.station.Station;
import com.metrovoc.phonon.station.StationView;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The screen of a speaker or boombox. The left column browses: tracks, queue, playlists and stations.
 * The right column is what's playing and, when it's not simply yours, who a button affects.
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
    private static final int LIST_Y = 46;
    private static final int LIST_HEIGHT = 186;
    private static final int TITLE_Y = 21;
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
    private static final int METER_QUIET = 0x3840FF40;
    private static final int SELECTED_Y = 151;
    private static final int MAX_SELECTED_LINES = 4;
    private static final int RESULT_PICTURE_WIDTH = 64;
    private static final int RESULT_PICTURE_HEIGHT = 36;
    private static final int MAX_RESULT_TITLE_LINES = 3;
    private static final double MAX_USE_DISTANCE_SQUARED = 64.0;
    /** Now Playing's long names run one way and wrap around, resting at the start of each loop. */
    private static final float MARQUEE_PIXELS_PER_SECOND = 24;
    private static final int MARQUEE_GAP = 24;
    private static final long MARQUEE_REST_MS = 2000;

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
    private static final String[] ICON_LIST = {
        "###......", ".........", "#####.#..", "......#..", "###.#####",
        "......#..", "#####.#..", ".........", "........."
    };
    private static final String[] ICON_UP_NEXT = {
        "##.....#.", "####...#.", "######.#.", "########.", "######.#.",
        "####...#.", "##.....#.", ".........", "#########"
    };
    private static final String[] ICON_SEARCH = {
        "..####...", ".#....#..", "#......#.", "#......#.", "#......#.",
        ".#....#..", "..####.#.", ".......##", "........#"
    };
    private static final String[] ICON_GRIP = {
        ".........", "#########", ".........", ".........", "#########",
        ".........", ".........", "#########", "........."
    };
    private static final String[] ICON_CHECK = {
        ".........", "........#", ".......##", "#.....##.", "##...##..",
        ".##.##...", "..###....", "...#.....", "........."
    };
    private static final String[] ICON_PENCIL = {
        ".......##", "......###", ".....###.", "....###..", "...###...",
        "..###....", ".###.....", ".##......", "#........"
    };
    private static final String[] ICON_DOWN = {
        ".........", ".........", "#########", ".#######.", "..#####..",
        "...###...", "....#....", ".........", "........."
    };

    private static final Component PLAY = Component.translatable("gui.phonon.speaker.play");
    private static final Component PAUSE = Component.translatable("gui.phonon.speaker.pause");
    private static final Component RESUME = Component.translatable("gui.phonon.speaker.resume");
    private static final Component STOP = Component.translatable("gui.phonon.speaker.stop");
    private static final Component PREVIOUS = Component.translatable("gui.phonon.speaker.previous");
    private static final Component NEXT = Component.translatable("gui.phonon.speaker.next");
    private static final Component SHUFFLE = Component.translatable("gui.phonon.speaker.shuffle");

    private enum Tab { TRACKS, QUEUE, PLAYLISTS, STATIONS }

    private final List<TabButton> tabs = new ArrayList<>();
    private EditBox field;
    private RowList list;
    private VolumeSlider volumeSlider;
    private ProgressSlider progressSlider;
    private SteelButton shuffleButton;
    private SteelButton previousButton;
    private SteelButton mainButton;
    private SteelButton nextButton;
    private SteelButton repeatButton;
    private SteelButton stopButton;
    private TextLink leaveLink;

    private Tab tab = Tab.TRACKS;
    @Nullable private UUID selectedTrack;
    @Nullable private SearchResult selectedResult;
    @Nullable private UUID openPlaylist;
    @Nullable private UUID confirmDelete;
    @Nullable private UUID trackForNewPlaylist;
    @Nullable private String playlistToOpen;
    @Nullable private PopupMenu popup;
    private boolean renaming;
    private int mouseX;
    private int mouseY;
    /** A click finishes by focusing what was clicked, so the field takes focus after it. */
    private boolean focusFieldNextTick;
    private String search = "";
    private int listScroll = -1;
    private final float[] meter = new float[METER_BARS];
    private String marqueeName = "";
    private long marqueeMs;
    private long marqueeLastNanos;

    public SpeakerScreen(SpeakerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, WIDTH, HEIGHT);
    }

    private Receiver pos() {
        return menu.receiver();
    }

    /** A speaker within reach, or a boombox still in the player's inventory. */
    private boolean stillAvailable() {
        return switch (pos()) {
            case Receiver.Block block -> minecraft.level.getBlockEntity(block.pos()) instanceof SpeakerBlockEntity
                && minecraft.player.distanceToSqr(block.pos().getX() + 0.5, block.pos().getY() + 0.5, block.pos().getZ() + 0.5)
                    <= MAX_USE_DISTANCE_SQUARED;
            case Receiver.Boombox boombox -> BoomboxItem.carries(minecraft.player, boombox.id());
        };
    }

    // What this player may do. The server has the final say; this only keeps the screen honest.

    @Nullable
    private UUID me() {
        return minecraft.player == null ? null : minecraft.player.getUUID();
    }

    private String ownerName() {
        String name = pos() instanceof Receiver.Block block && minecraft.level.getBlockEntity(block.pos()) instanceof SpeakerBlockEntity speaker
            ? speaker.getOwnerName() : "";
        return name.isEmpty() ? Component.translatable("gui.phonon.speaker.someone").getString() : name;
    }

    private boolean canTune() {
        if (!ClientStations.getInstance().ownerOnlySpeakers() || GuiPort.isOperator() || !(pos() instanceof Receiver.Block block)) {
            return true;
        }
        UUID owner = minecraft.level.getBlockEntity(block.pos()) instanceof SpeakerBlockEntity speaker ? speaker.getOwnerId() : null;
        return owner == null || owner.equals(me());
    }

    private boolean canControl() {
        StationView view = station();
        return view != null && view.shared() && !ClientStations.getInstance().anyoneCanControlStations()
            ? mayManage(view.ownerId())
            : canTune();
    }

    private boolean canAddTracks() {
        return ClientStations.getInstance().anyoneCanAddTracks() || GuiPort.isOperator();
    }

    private boolean canDelete(AudioResource resource) {
        return GuiPort.isOperator() || (resource.addedBy() != null && resource.addedBy().equals(me()));
    }

    private boolean mayManage(@Nullable UUID owner) {
        return GuiPort.isOperator() || (owner != null && owner.equals(me()));
    }

    /** What the transport buttons do to other people, or why they're locked. Empty on your own music. */
    private String scopeText() {
        StationView view = station();
        if (view != null && view.shared()) {
            return canControl()
                ? label("gui.phonon.speaker.changes_everyone").getString()
                : Component.translatable("gui.phonon.speaker.picks_music", view.ownerName()).getString();
        }
        return canTune() ? "" : Component.translatable("gui.phonon.speaker.only_owner", ownerName()).getString();
    }

    @Override
    protected void init() {
        super.init();
        popup = null;
        ClientSpeakerManager.getInstance().holdNowPlaying();

        tabs.clear();
        int tabX = leftPos + PADDING;
        for (Tab each : Tab.values()) {
            Component tabLabel = Component.translatable(switch (each) {
                case TRACKS -> "gui.phonon.speaker.tracks";
                case QUEUE -> "gui.phonon.speaker.queue";
                case PLAYLISTS -> "gui.phonon.speaker.playlists";
                case STATIONS -> "gui.phonon.speaker.stations";
            });
            int width = font.width(tabLabel);
            TabButton button = new TabButton(tabX, topPos + TAB_Y, width, TAB_HEIGHT, tabLabel, () -> openTab(each));
            tabs.add(button);
            addRenderableWidget(button);
            tabX += width + TAB_GAP;
        }

        field = new EditBox(font, leftPos + PADDING, topPos + FIELD_Y, LEFT_WIDTH, FIELD_HEIGHT,
            Component.translatable("gui.phonon.speaker.search"));
        field.setMaxLength(2048);
        field.setValue(search);
        field.setResponder(this::onFieldChanged);
        addRenderableWidget(field);

        list = new RowList(leftPos + PADDING, topPos + LIST_Y, LEFT_WIDTH, LIST_HEIGHT, ICON_GRIP, ICON_NOTE);
        list.setOnMoved(this::onTrackMoved);
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

        Component leave = label("gui.phonon.speaker.leave");
        int leaveWidth = font.width(leave);
        leaveLink = new TextLink(x + RIGHT_WIDTH - leaveWidth, topPos + STATUS_Y, leaveWidth, 11, leave, () -> send(SpeakerAction.TUNE, null));
        addRenderableWidget(leaveLink);

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

    private SteelButton addTransport(int x, int y, int width, Component tip, String[] icon, Runnable action) {
        SteelButton button = new SteelButton(x, y, width, TRANSPORT_HEIGHT, tip, icon, action);
        addRenderableWidget(button);
        return button;
    }

    @Override
    public void removed() {
        super.removed();
        ClientSpeakerManager.getInstance().releaseNowPlaying();
        ClientSearch.getInstance().clear();
    }

    @Override
    EditBox textField() {
        return field;
    }

    private void send(SpeakerAction action) {
        send(action, null, null, 0, "");
    }

    private void send(SpeakerAction action, @Nullable UUID target) {
        send(action, target, null, 0, "");
    }

    private void send(SpeakerAction action, @Nullable UUID target, @Nullable UUID second) {
        send(action, target, second, 0, "");
    }

    private void send(SpeakerAction action, @Nullable UUID target, @Nullable UUID second, int number, String text) {
        PlatformHelper.INSTANCE.sendSpeakerAction(pos(), action, target, second, number, text);
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

    private static Component label(String key) {
        return Component.translatable(key);
    }

    private void clearField() {
        search = "";
        field.setValue("");
    }

    private void endSearch() {
        selectedResult = null;
        ClientSearch.getInstance().clear();
    }

    private void openTab(Tab next) {
        tab = next;
        openPlaylist = null;
        confirmDelete = null;
        renaming = false;
        trackForNewPlaylist = null;
        playlistToOpen = null;
        endSearch();
        clearField();
        list.setScroll(0);
        applyTab();
        refreshRows();
    }

    /** Shows the text field the way the open tab uses it. */
    private void applyTab() {
        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).setOpen(tab.ordinal() == i);
        }
        Playlist open = playlist(openPlaylist);
        field.visible = tab != Tab.QUEUE && (open == null || mayManage(open.ownerId()));
        field.setHint(label(renaming ? "gui.phonon.speaker.rename_hint" : switch (tab) {
            case TRACKS -> canAddTracks() ? "gui.phonon.speaker.add_hint" : "gui.phonon.speaker.search_hint";
            case PLAYLISTS -> open != null ? "gui.phonon.speaker.add_songs_hint" : "gui.phonon.speaker.playlist_hint";
            case STATIONS -> canTune() ? "gui.phonon.speaker.station_hint" : "gui.phonon.speaker.station_hint_locked";
            case QUEUE -> "gui.phonon.speaker.search_hint";
        }));
    }

    private void onFieldChanged(String value) {
        search = value;
        confirmDelete = null;
        ClientSearch results = ClientSearch.getInstance();
        if (tab == Tab.TRACKS && results.active() && !value.strip().equals(results.query())) {
            endSearch();
        }
        refreshRows();
    }

    private static boolean isLink(String text) {
        return AddRequest.parse(text) != null;
    }

    private void startSearch(String text, boolean updateFirst) {
        selectedResult = null;
        list.setScroll(0);
        ClientSearch results = ClientSearch.getInstance();
        int id = results.start(text, isLink(text), updateFirst);
        PlatformHelper.INSTANCE.sendSearch(id, results.source(), text, updateFirst);
        refreshRows();
    }

    private void searchOn(SearchSource source, String text) {
        ClientSearch.getInstance().chooseSource(source);
        startSearch(text, false);
    }

    @Override
    void submitField() {
        String text = field.getValue().strip();
        if (text.isEmpty()) {
            return;
        }
        if (renaming) {
            send(SpeakerAction.RENAME_PLAYLIST, openPlaylist, null, 0, text);
            renaming = false;
            applyTab();
            clearField();
            return;
        }
        switch (tab) {
            case TRACKS -> {
                if (!canAddTracks()) {
                    return;
                }
                if (isLink(text)) {
                    send(SpeakerAction.ADD_LINK, null, null, 0, text);
                    clearField();
                } else {
                    startSearch(text, false);
                }
            }
            case PLAYLISTS -> {
                Playlist open = playlist(openPlaylist);
                if (open != null) {
                    libraryMatches(text).stream().filter(each -> !open.tracks().contains(each.id())).findFirst()
                        .ifPresent(each -> send(SpeakerAction.ADD_TO_PLAYLIST, open.id(), each.id()));
                    clearField();
                } else {
                    createOrOpenPlaylist(text);
                }
            }
            case STATIONS -> {
                if (canTune()) {
                    send(SpeakerAction.CREATE_STATION, null, null, 0, text);
                    clearField();
                }
            }
            case QUEUE -> {
            }
        }
    }

    private void createOrOpenPlaylist(String text) {
        Playlist existing = ClientStations.getInstance().playlists().stream()
            .filter(each -> each.name().equalsIgnoreCase(text)).findFirst().orElse(null);
        if (existing != null) {
            if (trackForNewPlaylist != null && mayManage(existing.ownerId())) {
                send(SpeakerAction.ADD_TO_PLAYLIST, existing.id(), trackForNewPlaylist);
            }
            trackForNewPlaylist = null;
            openPlaylistView(existing.id());
            return;
        }
        send(SpeakerAction.CREATE_PLAYLIST, null, trackForNewPlaylist, 0, text);
        trackForNewPlaylist = null;
        /* The server shortens names to this, and the new playlist opens once it arrives. */
        playlistToOpen = text.substring(0, Math.min(text.length(), Station.MAX_NAME_CHARS)).strip();
        clearField();
    }

    private static List<AudioResource> libraryMatches(String text) {
        return text.isBlank() ? ClientAudioManager.getInstance().getAllResources() : ClientAudioManager.getInstance().searchResources(text.strip());
    }

    private List<FormattedCharSequence> wrapped(String key) {
        return font.split(Component.translatable(key), LEFT_WIDTH - 16);
    }

    private void refreshRows() {
        if (playlistToOpen != null) {
            for (Playlist each : ClientStations.getInstance().playlists()) {
                if (each.name().equals(playlistToOpen) && each.ownerId().equals(me())) {
                    openPlaylist = each.id();
                    playlistToOpen = null;
                    clearField();
                    list.setScroll(0);
                    applyTab();
                    break;
                }
            }
        }
        switch (tab) {
            case TRACKS -> list.setRows(trackRows(), wrapped(canAddTracks() ? "gui.phonon.speaker.empty_operator"
                : search.isBlank() ? "gui.phonon.speaker.empty_player" : "gui.phonon.speaker.no_match"));
            case QUEUE -> list.setRows(queueRows(), wrapped("gui.phonon.speaker.queue_empty"));
            case PLAYLISTS -> {
                Playlist open = playlist(openPlaylist);
                if (open != null) {
                    list.setRows(playlistTrackRows(open), List.of());
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

    // Tracks

    private List<RowList.Row> trackRows() {
        List<RowList.Row> rows = new ArrayList<>();
        ClientSearch results = ClientSearch.getInstance();
        for (LibraryJob job : ClientStations.getInstance().jobs()) {
            boolean ownRow = results.results().stream().anyMatch(each -> each.url().equals(job.url()));
            if (!ownRow) {
                rows.add(new RowList.Row(job.label()).color(SteelStyle.TEXT_MUTED)
                    .detail(job.percent() >= 0 ? job.percent() + "%" : "...").progress(Math.max(0, job.percent())));
            }
        }
        if (results.active()) {
            addSearchRows(rows, results);
            return rows;
        }
        String text = search.strip();
        if (isLink(text)) {
            if (canAddTracks()) {
                rows.add(new RowList.Row(label("gui.phonon.speaker.add_this_link").getString()).call().marker(ICON_PLUS, SteelStyle.PLAYING)
                    .detail(label("gui.phonon.speaker.enter").getString()).click(this::submitField));
            }
            return rows;
        }
        PlaybackState state = currentState();
        for (AudioResource resource : libraryMatches(text)) {
            rows.add(trackRow(resource, state));
        }
        if (!text.isEmpty() && canAddTracks()) {
            rows.add(new RowList.Row(Component.translatable("gui.phonon.speaker.find_on", text, results.source().label()).getString())
                .call().marker(ICON_SEARCH, SteelStyle.PLAYING).detail(label("gui.phonon.speaker.enter").getString()).click(this::submitField));
        }
        return rows;
    }

    private RowList.Row trackRow(AudioResource resource, PlaybackState state) {
        boolean loaded = resource.id().equals(state.resourceId());
        int color = !loaded ? SteelStyle.TEXT_DIM : state.isPaused() ? SteelStyle.PAUSED : SteelStyle.PLAYING;
        boolean selected = resource.id().equals(selectedTrack);
        List<RowList.Action> actions = new ArrayList<>();
        if (canControl()) {
            actions.add(new RowList.Action(ICON_PLAY, label("gui.phonon.speaker.action_play_now"), () -> send(SpeakerAction.PLAY_TRACK, resource.id())));
            actions.add(new RowList.Action(ICON_UP_NEXT, label("gui.phonon.speaker.action_play_next"), () -> send(SpeakerAction.PLAY_NEXT, resource.id())));
        }
        actions.add(new RowList.Action(ICON_LIST, label("gui.phonon.speaker.action_add_to_playlist"), () -> openPlaylistMenu(resource.id())));
        if (canDelete(resource)) {
            boolean confirming = resource.id().equals(confirmDelete);
            actions.add(new RowList.Action(ICON_TRASH,
                label(confirming ? "gui.phonon.speaker.action_confirm_track" : "gui.phonon.speaker.action_delete_track"),
                () -> {
                    if (resource.id().equals(confirmDelete)) {
                        confirmDelete = null;
                        send(SpeakerAction.DELETE_TRACK, resource.id());
                    } else {
                        confirmDelete = resource.id();
                    }
                }));
        }
        return new RowList.Row(resource.name()).key(resource.id()).detail(SteelStyle.time(resource.durationMs()))
            .color(loaded ? color : selected ? SteelStyle.TEXT : SteelStyle.TEXT_DIM).selected(selected)
            .marker(loaded ? ICON_NOTE : null, color).actions(actions)
            .click(() -> selectTrack(resource.id()))
            .doubleClick(canControl() ? () -> send(SpeakerAction.PLAY_TRACK, resource.id()) : null);
    }

    private void addSearchRows(List<RowList.Row> rows, ClientSearch results) {
        rows.add(new RowList.Row(results.query()).header(() -> {
            endSearch();
            clearField();
            focusFieldNextTick = true;
            refreshRows();
        }).marker(ICON_BACK, SteelStyle.TEXT).tooltip(label("gui.phonon.speaker.back_to_library")));
        if (!results.isLink()) {
            rows.add(new RowList.Row(label("gui.phonon.speaker.results_from").getString()).color(SteelStyle.TEXT_MUTED)
                .detail(results.source().label()).detailClick(this::openSourceMenu, ICON_DOWN, label("gui.phonon.speaker.choose_source")));
        }
        if (results.updating()) {
            rows.add(new RowList.Row(label("gui.phonon.search.updating").getString()).line(SteelStyle.TEXT_DIM));
            return;
        }
        if (results.searching()) {
            addLines(rows, Component.translatable("gui.phonon.speaker.searching", results.query()).getString(), SteelStyle.TEXT_DIM);
            return;
        }
        if (results.failure() != null) {
            addFailureRows(rows, results);
            return;
        }
        if (results.results().isEmpty()) {
            addLines(rows, Component.translatable("gui.phonon.speaker.no_results", results.query()).getString(), SteelStyle.TEXT_DIM);
            return;
        }
        for (SearchResult result : results.results()) {
            rows.add(resultRow(result, results));
        }
    }

    private void addLines(List<RowList.Row> rows, String text, int color) {
        font.getSplitter().splitLines(text, LEFT_WIDTH - 16, Style.EMPTY)
            .forEach(line -> rows.add(new RowList.Row(line.getString()).line(color)));
    }

    /** Says what happened, and offers whatever could actually help. */
    private void addFailureRows(List<RowList.Row> rows, ClientSearch results) {
        AudioFailure failure = results.failure();
        String query = results.query();
        addLines(rows, label(failure == AudioFailure.UNKNOWN ? "gui.phonon.search.failed" : failure.translationKey()).getString(), SteelStyle.TEXT);
        switch (failure) {
            case BOT_CHECK -> {
                if (!results.isLink() && results.source() != SearchSource.SOUNDCLOUD) {
                    rows.add(tryElsewhere(SearchSource.SOUNDCLOUD, query));
                }
                if (GuiPort.isOperator()) {
                    addLines(rows, label("gui.phonon.search.cloud_note").getString(), SteelStyle.PAUSED);
                }
            }
            case REFUSED, RATE_LIMITED, NETWORK, TIMEOUT -> {
                rows.add(new RowList.Row(label("gui.phonon.search.try_again").getString()).button(() -> startSearch(query, false)));
                if (failure == AudioFailure.REFUSED && results.source() != SearchSource.YOUTUBE) {
                    rows.add(tryElsewhere(SearchSource.YOUTUBE, query));
                }
            }
            case UNKNOWN, UNSUPPORTED -> {
                addLines(rows, label("gui.phonon.search.outdated").getString(), SteelStyle.TEXT_DIM);
                if (GuiPort.isOperator()) {
                    rows.add(new RowList.Row(label("gui.phonon.search.update_retry").getString()).button(() -> startSearch(query, true)));
                } else {
                    addLines(rows, label("gui.phonon.search.ask_operator").getString(), SteelStyle.TEXT_DIM);
                }
            }
            default -> {
            }
        }
    }

    private RowList.Row tryElsewhere(SearchSource source, String query) {
        return new RowList.Row(Component.translatable("gui.phonon.search.try_source", source.label()).getString())
            .button(() -> searchOn(source, query));
    }

    private static boolean inLibrary(SearchResult result) {
        return ClientAudioManager.getInstance().getAllResources().stream().anyMatch(each -> each.url().equals(result.url()));
    }

    private RowList.Row resultRow(SearchResult result, ClientSearch results) {
        LibraryJob job = ClientStations.getInstance().jobs().stream().filter(each -> each.url().equals(result.url())).findFirst().orElse(null);
        boolean done = job == null && inLibrary(result);
        boolean adding = job != null || (!done && results.wasAdded(result.key()));
        AudioFailure failed = adding || done ? null : results.failedAdd(result.url());
        boolean selected = selectedResult != null && selectedResult.key().equals(result.key());
        int percent = job == null ? 0 : Math.max(0, job.percent());
        String status = done ? label("gui.phonon.speaker.in_library").getString()
            : adding ? Component.translatable("gui.phonon.speaker.adding_percent", percent).getString()
            : failed != null ? label("gui.phonon.search.add_failed").getString()
            : result.uploader() + " · " + SteelStyle.time(result.durationMs());
        RowList.Row row = new RowList.Row(result.title()).picture(results.picture(result.key()))
            .color(done ? SteelStyle.PLAYING : selected ? SteelStyle.TEXT : SteelStyle.TEXT_DIM).selected(selected)
            .subtext(status, failed != null ? SteelStyle.PAUSED : adding ? SteelStyle.TEXT_DIM : SteelStyle.TEXT_MUTED)
            .tooltip(failed != null ? label(failed.translationKey()) : null)
            .click(() -> {
                selectedResult = result;
                selectedTrack = null;
                refreshRows();
            });
        if (adding) {
            row.progress(percent);
        } else if (!done) {
            row.actions(List.of(new RowList.Action(ICON_PLUS, label("gui.phonon.speaker.action_add"), () -> addResult(result))));
            row.doubleClick(() -> addResult(result));
        }
        return row;
    }

    private void addResult(SearchResult result) {
        ClientSearch.getInstance().markAdded(result);
        send(SpeakerAction.ADD_RESULT, null, null, 0, result.title() + "\n" + result.url());
    }

    // Menus

    private void openSourceMenu(int atX, int atY) {
        ClientSearch results = ClientSearch.getInstance();
        List<PopupMenu.Item> items = new ArrayList<>();
        for (SearchSource source : SearchSource.values()) {
            items.add(new PopupMenu.Item(source.label(), source == results.source(),
                source == SearchSource.BILIBILI ? label("gui.phonon.speaker.source_refuses") : null,
                () -> {
                    if (source != results.source() || results.failure() != null) {
                        searchOn(source, results.query());
                    }
                }));
        }
        showMenu(new PopupMenu(label("gui.phonon.speaker.search_on"), items, ICON_CHECK, leftPos + PADDING + LEFT_WIDTH, atY + 6, topPos + HEIGHT));
    }

    private void openPlaylistMenu(UUID trackId) {
        List<PopupMenu.Item> items = new ArrayList<>();
        for (Playlist each : ClientStations.getInstance().playlists()) {
            if (mayManage(each.ownerId())) {
                boolean in = each.tracks().contains(trackId);
                items.add(new PopupMenu.Item(each.name(), in, null,
                    () -> send(in ? SpeakerAction.REMOVE_FROM_PLAYLIST : SpeakerAction.ADD_TO_PLAYLIST, each.id(), trackId)));
            }
        }
        items.add(new PopupMenu.Item(label("gui.phonon.speaker.new_playlist").getString(), false, null, () -> {
            openTab(Tab.PLAYLISTS);
            trackForNewPlaylist = trackId;
            focusFieldNextTick = true;
        }));
        int right = Math.min(mouseX + 40, leftPos + WIDTH - PADDING);
        showMenu(new PopupMenu(label("gui.phonon.speaker.add_to_playlist_title"), items, ICON_CHECK, right, mouseY + 6, topPos + HEIGHT));
    }

    private void showMenu(PopupMenu next) {
        popup = next;
        list.setBlocked(true);
    }

    @Override
    boolean overlayPressed(double x, double y) {
        if (popup == null) {
            return false;
        }
        PopupMenu closing = popup;
        popup = null;
        list.setBlocked(false);
        closing.press(x, y);
        return true;
    }

    @Override
    boolean overlayScrolled(double amount) {
        if (popup == null) {
            return false;
        }
        popup.scrolled(amount);
        return true;
    }

    // Queue

    private List<RowList.Row> queueRows() {
        List<RowList.Row> rows = new ArrayList<>();
        StationView view = station();
        if (view == null) {
            return rows;
        }
        PlaybackState state = currentState();
        boolean control = canControl();
        for (int i = 0; i < view.queue().size(); i++) {
            int index = i;
            UUID id = view.queue().get(i);
            boolean current = i == view.cursor() && !state.isStopped();
            int color = !current ? SteelStyle.TEXT_DIM : state.isPaused() ? SteelStyle.PAUSED : SteelStyle.PLAYING;
            Optional<AudioResource> resource = track(id);
            rows.add(new RowList.Row(resource.map(AudioResource::name).orElse("?")).key(id)
                .detail(SteelStyle.time(resource.map(AudioResource::durationMs).orElse(-1L))).color(color).selected(id.equals(selectedTrack))
                .marker(current ? ICON_NOTE : null, color)
                .actions(control ? List.of(new RowList.Action(ICON_REMOVE, label("gui.phonon.speaker.action_remove"),
                    () -> send(SpeakerAction.REMOVE_FROM_QUEUE, null, null, index, ""))) : List.of())
                .click(() -> selectTrack(id))
                .doubleClick(control ? () -> send(SpeakerAction.JUMP_TO, null, null, index, "") : null));
        }
        return rows;
    }

    // Playlists

    private List<RowList.Row> playlistRows() {
        List<RowList.Row> rows = new ArrayList<>();
        String text = search.strip();
        String lower = text.toLowerCase(Locale.ROOT);
        boolean exact = false;
        for (Playlist each : ClientStations.getInstance().playlists()) {
            exact |= each.name().equalsIgnoreCase(text);
            if (!each.name().toLowerCase(Locale.ROOT).contains(lower)) {
                continue;
            }
            rows.add(new RowList.Row(each.name()).head(each.ownerId()).color(SteelStyle.TEXT).detail(songCount(each.tracks().size()))
                .actions(canControl() && !each.tracks().isEmpty()
                    ? List.of(new RowList.Action(ICON_PLAY, label("gui.phonon.speaker.action_play"), () -> send(SpeakerAction.PLAY_PLAYLIST, each.id())))
                    : List.of())
                .click(() -> openPlaylistView(each.id())));
        }
        if (!text.isEmpty() && !exact) {
            rows.add(new RowList.Row(Component.translatable("gui.phonon.speaker.create_named", text).getString()).call().marker(ICON_PLUS, SteelStyle.PLAYING)
                .detail(label("gui.phonon.speaker.enter").getString()).click(this::submitField));
        }
        return rows;
    }

    private static String songCount(int count) {
        return count == 1 ? label("gui.phonon.speaker.one_song").getString() : Component.translatable("gui.phonon.speaker.songs", count).getString();
    }

    private void openPlaylistView(UUID id) {
        openPlaylist = id;
        confirmDelete = null;
        renaming = false;
        clearField();
        list.setScroll(0);
        applyTab();
        refreshRows();
    }

    private void closePlaylistView() {
        openPlaylist = null;
        renaming = false;
        clearField();
        list.setScroll(0);
        applyTab();
        refreshRows();
    }

    private List<RowList.Row> playlistTrackRows(Playlist open) {
        List<RowList.Row> rows = new ArrayList<>();
        boolean edit = mayManage(open.ownerId());
        boolean control = canControl();
        List<RowList.Action> actions = new ArrayList<>();
        if (control && !open.tracks().isEmpty()) {
            actions.add(new RowList.Action(ICON_PLAY, label("gui.phonon.speaker.action_play_start"), () -> send(SpeakerAction.PLAY_PLAYLIST, open.id())));
        }
        if (edit) {
            actions.add(new RowList.Action(ICON_PENCIL, label("gui.phonon.speaker.action_rename"), () -> {
                renaming = true;
                search = open.name();
                field.setValue(open.name());
                applyTab();
                focusFieldNextTick = true;
            }));
            boolean confirming = open.id().equals(confirmDelete);
            actions.add(new RowList.Action(ICON_TRASH,
                label(confirming ? "gui.phonon.speaker.action_confirm_playlist" : "gui.phonon.speaker.action_delete_playlist"),
                () -> {
                    if (open.id().equals(confirmDelete)) {
                        confirmDelete = null;
                        send(SpeakerAction.DELETE_PLAYLIST, open.id());
                        closePlaylistView();
                    } else {
                        confirmDelete = open.id();
                    }
                }));
        }
        rows.add(new RowList.Row(open.name()).header(this::closePlaylistView).marker(ICON_BACK, SteelStyle.TEXT).head(open.ownerId()).actions(actions)
            .tooltip(label("gui.phonon.speaker.back")));

        String text = search.strip();
        if (!text.isEmpty() && !renaming) {
            List<AudioResource> matches = libraryMatches(text);
            for (AudioResource resource : matches) {
                boolean in = open.tracks().contains(resource.id());
                rows.add(new RowList.Row(resource.name()).color(in ? SteelStyle.PLAYING : SteelStyle.TEXT)
                    .marker(in ? ICON_CHECK : ICON_PLUS, in ? SteelStyle.PLAYING : SteelStyle.TEXT_DIM)
                    .detail(in ? label("gui.phonon.speaker.in_playlist").getString() : SteelStyle.time(resource.durationMs()))
                    .tooltip(label(in ? "gui.phonon.speaker.click_to_take_out" : "gui.phonon.speaker.click_to_add"))
                    .click(() -> send(in ? SpeakerAction.REMOVE_FROM_PLAYLIST : SpeakerAction.ADD_TO_PLAYLIST, open.id(), resource.id())));
            }
            if (matches.isEmpty()) {
                addLines(rows, Component.translatable("gui.phonon.speaker.no_songs_match", text).getString(), SteelStyle.TEXT_DIM);
            }
            return rows;
        }
        if (open.tracks().isEmpty()) {
            addLines(rows, label(edit ? "gui.phonon.speaker.playlist_empty_edit" : "gui.phonon.speaker.playlist_empty_view").getString(), SteelStyle.TEXT_DIM);
            return rows;
        }
        PlaybackState state = currentState();
        for (int i = 0; i < open.tracks().size(); i++) {
            int index = i;
            UUID id = open.tracks().get(i);
            Optional<AudioResource> resource = track(id);
            boolean loaded = id.equals(state.resourceId());
            RowList.Row row = new RowList.Row(resource.map(AudioResource::name).orElse("?")).key(id)
                .detail(SteelStyle.time(resource.map(AudioResource::durationMs).orElse(-1L)))
                .color(loaded ? SteelStyle.PLAYING : SteelStyle.TEXT_DIM).selected(id.equals(selectedTrack))
                .actions(edit ? List.of(new RowList.Action(ICON_REMOVE, label("gui.phonon.speaker.action_take_out"),
                    () -> send(SpeakerAction.REMOVE_FROM_PLAYLIST, open.id(), id))) : List.of())
                .click(() -> selectTrack(id))
                .doubleClick(control ? () -> send(SpeakerAction.PLAY_PLAYLIST, open.id(), null, index, "") : null);
            if (edit) {
                row.grip(label("gui.phonon.speaker.drag_to_reorder"));
            }
            rows.add(row);
        }
        return rows;
    }

    private void onTrackMoved(RowList.Row row, int place) {
        UUID moved = list.keyOf(row);
        if (moved != null && openPlaylist != null) {
            ClientStations.getInstance().moveInPlaylist(openPlaylist, moved, place);
            send(SpeakerAction.MOVE_IN_PLAYLIST, openPlaylist, moved, place, "");
        }
    }

    // Stations

    private List<RowList.Row> stationRows() {
        List<RowList.Row> rows = new ArrayList<>();
        StationView tuned = station();
        boolean onOwn = tuned == null || !tuned.shared();
        boolean tune = canTune();
        Component locked = Component.translatable("gui.phonon.speaker.only_owner", ownerName());
        String own = label(pos() instanceof Receiver.Boombox ? "gui.phonon.speaker.this_boombox" : "gui.phonon.speaker.this_speaker").getString();
        rows.add(new RowList.Row(own).color(onOwn ? SteelStyle.PLAYING : SteelStyle.TEXT_DIM).marker(onOwn ? ICON_NOTE : null, SteelStyle.PLAYING)
            .detail(label("gui.phonon.speaker.own_music").getString())
            .tooltip(tune ? (onOwn ? null : label("gui.phonon.speaker.own_music_tip")) : locked)
            .click(tune && !onOwn ? () -> send(SpeakerAction.TUNE, null) : null));
        String text = search.strip();
        String lower = text.toLowerCase(Locale.ROOT);
        boolean exact = false;
        for (StationView view : ClientStations.getInstance().shared()) {
            exact |= view.name().equalsIgnoreCase(text);
            if (!view.name().toLowerCase(Locale.ROOT).contains(lower)) {
                continue;
            }
            boolean on = tuned != null && view.id().equals(tuned.id());
            boolean confirming = view.id().equals(confirmDelete);
            UUID current = view.cursor() >= 0 && view.cursor() < view.queue().size() ? view.queue().get(view.cursor()) : null;
            String who = view.ownerId() != null && view.ownerId().equals(me())
                ? label("gui.phonon.speaker.you_run_it").getString()
                : Component.translatable("gui.phonon.speaker.run_by", view.ownerName()).getString();
            String sub = track(current).map(each -> who + " · " + each.name()).orElse(who);
            rows.add(new RowList.Row(view.name()).head(view.ownerId()).color(on ? SteelStyle.PLAYING : SteelStyle.TEXT).subtext(sub, SteelStyle.TEXT_MUTED)
                .actions(mayManage(view.ownerId()) ? List.of(new RowList.Action(ICON_TRASH,
                    label(confirming ? "gui.phonon.speaker.action_confirm_station" : "gui.phonon.speaker.action_delete_station"),
                    () -> {
                        if (view.id().equals(confirmDelete)) {
                            confirmDelete = null;
                            send(SpeakerAction.DELETE_STATION, view.id());
                        } else {
                            confirmDelete = view.id();
                        }
                    })) : List.of())
                .tooltip(!tune ? locked : label(on ? "gui.phonon.speaker.listening" : "gui.phonon.speaker.click_to_listen"))
                .click(tune && !on ? () -> send(SpeakerAction.TUNE, view.id()) : null));
        }
        if (!text.isEmpty() && !exact && tune) {
            rows.add(new RowList.Row(Component.translatable("gui.phonon.speaker.start_named", text).getString()).call().marker(ICON_PLUS, SteelStyle.PLAYING)
                .detail(label("gui.phonon.speaker.enter").getString()).tooltip(label("gui.phonon.speaker.start_tip")).click(this::submitField));
        }
        return rows;
    }

    private void selectTrack(UUID id) {
        selectedTrack = id;
        selectedResult = null;
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

    /** One button, one meaning: it pauses and resumes. Songs start from their row. */
    private void pressMain() {
        PlaybackState state = currentState();
        if (state.isPlaying()) {
            send(SpeakerAction.PAUSE);
        } else if (state.isPaused()) {
            send(SpeakerAction.RESUME);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (minecraft.player == null || minecraft.level == null || !stillAvailable()) {
            onClose();
            return;
        }
        if (focusFieldNextTick) {
            focusFieldNextTick = false;
            setFocused(field);
        }
        listScroll = list.scroll();
        applyTab();
        refreshRows();
        updateControls();
    }

    private void updateControls() {
        PlaybackState state = currentState();
        boolean loaded = !state.isStopped();
        StationView view = station();
        boolean control = canControl();

        progressSlider.update(loaded ? state.getCurrentPositionMs() : 0,
            loaded ? track(state.resourceId()).map(AudioResource::durationMs).orElse(-1L) : -1);
        progressSlider.active = control;
        volumeSlider.follow(ClientSpeakerManager.getInstance().getSpeakerVolume(pos()));
        volumeSlider.active = canTune();

        mainButton.show(state.isPlaying() ? PAUSE : state.isPaused() ? RESUME : PLAY, state.isPlaying() ? ICON_PAUSE : ICON_PLAY);
        mainButton.active = control && loaded;
        stopButton.active = control && loaded;
        boolean anything = control && !ClientAudioManager.getInstance().getAllResources().isEmpty();
        previousButton.active = anything;
        nextButton.active = anything;
        shuffleButton.active = control;
        repeatButton.active = control;

        Station.Repeat repeat = view == null ? Station.Repeat.OFF : view.repeat();
        repeatButton.show(Component.translatable(switch (repeat) {
            case OFF -> "gui.phonon.speaker.repeat_off";
            case ALL -> "gui.phonon.speaker.repeat_all";
            case ONE -> "gui.phonon.speaker.repeat_one";
        }), repeat == Station.Repeat.ONE ? ICON_REPEAT_ONE : ICON_REPEAT);
        repeatButton.setLit(repeat != Station.Repeat.OFF);
        shuffleButton.setLit(view != null && view.shuffle());
        leaveLink.visible = view != null && view.shared() && canTune();
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
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        int leftX = leftPos + PADDING;
        int rightX = leftPos + RIGHT_X;

        if (tab == Tab.QUEUE) {
            drawQueueHeading(canvas, leftX);
        }
        canvas.text(Component.translatable("gui.phonon.speaker.now_playing"), rightX, topPos + LABEL_Y, SteelStyle.TEXT);
        drawNowPlaying(canvas, rightX, mouseX, mouseY);
        drawMeter(canvas, rightX);
        drawSelected(canvas, rightX);
        if (popup != null) {
            popup.draw(canvas, mouseX, mouseY);
        } else {
            drawControlTooltip(canvas, mouseX, mouseY);
        }
    }

    private void drawQueueHeading(GuiPort.Canvas canvas, int x) {
        StationView view = station();
        Component heading = view != null && view.shared()
            ? Component.translatable("gui.phonon.speaker.up_next_on", view.name())
            : label("gui.phonon.speaker.up_next_here");
        cropped(canvas, heading.getString(), x, topPos + FIELD_Y + 6, LEFT_WIDTH, SteelStyle.TEXT_MUTED);
    }

    /** Text that may not fit: cropped, with the whole of it as a tooltip. */
    private void cropped(GuiPort.Canvas canvas, String text, int x, int y, int room, int color) {
        boolean cut = canvas.font.width(text) > room;
        String shown = cut ? canvas.font.plainSubstrByWidth(text, Math.max(0, room - canvas.font.width("..."))) + "..." : text;
        canvas.text(shown, x, y, color);
        if (cut && popup == null && mouseX >= x && mouseX < x + room && mouseY >= y && mouseY < y + canvas.font.lineHeight) {
            canvas.tooltip(Component.literal(text), mouseX, mouseY);
        }
    }

    private void drawNowPlaying(GuiPort.Canvas canvas, int x, int mouseX, int mouseY) {
        PlaybackState state = currentState();
        int y = topPos + TITLE_Y;
        drawStatus(canvas, x, state, track(state.resourceId()).isPresent());
        if (state.isStopped()) {
            canvas.text(Component.translatable("gui.phonon.speaker.nothing_playing"), x, y, SteelStyle.TEXT_OFF);
            return;
        }
        String title = track(state.resourceId()).map(AudioResource::name)
            .orElseGet(() -> Component.translatable("gui.phonon.speaker.unknown").getString());
        int color = state.isPaused() ? SteelStyle.PAUSED : SteelStyle.PLAYING;
        int width = canvas.font.width(title);
        if (width <= RIGHT_WIDTH) {
            canvas.text(title, x, y, color);
            return;
        }
        int loop = width + MARQUEE_GAP;
        int offset = marqueeOffset(title, loop, state.isPlaying());
        canvas.clip(x, y - 1, x + RIGHT_WIDTH, y + canvas.font.lineHeight + 1);
        canvas.text(title, x - offset, y, color);
        canvas.text(title, x - offset + loop, y, color);
        canvas.unclip();
        if (mouseX >= x && mouseX < x + RIGHT_WIDTH && mouseY >= y && mouseY < y + canvas.font.lineHeight) {
            canvas.tooltip(Component.literal(title), mouseX, mouseY);
        }
    }

    /** Runs only while the music does, like a car stereo's display. */
    private int marqueeOffset(String title, int loop, boolean running) {
        long now = System.nanoTime();
        long elapsedMs = Math.min(100, (now - marqueeLastNanos) / 1_000_000L);
        marqueeLastNanos = now;
        if (!title.equals(marqueeName)) {
            marqueeName = title;
            marqueeMs = 0;
        } else if (running) {
            marqueeMs += elapsedMs;
        }
        long travelMs = (long) (loop / MARQUEE_PIXELS_PER_SECOND * 1000);
        long at = marqueeMs % (MARQUEE_REST_MS + travelMs);
        return at < MARQUEE_REST_MS ? 0 : (int) ((at - MARQUEE_REST_MS) * loop / travelMs);
    }

    /** The station you're on, or how your own music is doing, under the title. */
    private void drawStatus(GuiPort.Canvas canvas, int x, PlaybackState state, boolean available) {
        int y = topPos + STATUS_Y;
        Component status = status(state, available);
        StationView view = station();
        if (view == null || !view.shared()) {
            if (status != null) {
                canvas.text(status, x, y, SteelStyle.TEXT_MUTED);
            }
            return;
        }
        canvas.head(view.ownerId(), x, y, 8);
        String name = status == null ? view.name() : view.name() + " · " + status.getString();
        int room = RIGHT_WIDTH - 11 - (leaveLink.visible ? leaveLink.getWidth() + 4 : 0);
        cropped(canvas, name, x + 11, y, room, SteelStyle.TEXT_DIM);
    }

    @Nullable
    private Component status(PlaybackState state, boolean available) {
        if (state.isStopped()) {
            return null;
        }
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

    /** Recent loudness of what this speaker is playing, newest on the right, and what the buttons above it affect. */
    private void drawMeter(GuiPort.Canvas canvas, int x) {
        PlaybackState state = currentState();
        int bottom = topPos + METER_Y + METER_HEIGHT;
        canvas.fill(x, topPos + METER_Y, x + RIGHT_WIDTH, bottom, SteelStyle.WELL);
        if (state.isPlaying()) {
            PlatformHelper.INSTANCE.readAudioLevels(pos(), state.getCurrentPositionMs(), meter, METER_BAR_MS);
        } else {
            java.util.Arrays.fill(meter, 0);
        }
        String scope = scopeText();
        int barColor = scope.isEmpty() ? SteelStyle.PLAYING : METER_QUIET;
        for (int i = 0; i < METER_BARS; i++) {
            int height = Math.round(meter[i] * (METER_HEIGHT - 2));
            int left = x + 1 + i * (METER_BAR_WIDTH + 1);
            canvas.fill(left, bottom - 1 - height, left + METER_BAR_WIDTH, bottom - 1, barColor);
        }
        if (!scope.isEmpty()) {
            int room = RIGHT_WIDTH - 4;
            cropped(canvas, scope, x + (RIGHT_WIDTH - Math.min(room, canvas.font.width(scope))) / 2, topPos + METER_Y + 3, room, SteelStyle.TEXT);
        }
    }

    private void drawSelected(GuiPort.Canvas canvas, int x) {
        int y = topPos + SELECTED_Y;
        canvas.fill(x, y, x + RIGHT_WIDTH, y + 1, SteelStyle.DIVIDER);
        y += 6;
        canvas.text(Component.translatable("gui.phonon.speaker.selected"), x, y, SteelStyle.TEXT_MUTED);
        y += canvas.font.lineHeight + 3;

        if (selectedResult != null) {
            drawSelectedResult(canvas, selectedResult, x, y);
            return;
        }
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
        if (selected.get().addedBy() != null) {
            y += canvas.font.lineHeight + 1;
            canvas.text(Component.translatable("gui.phonon.speaker.added_by", selected.get().addedByName()), x, y, SteelStyle.TEXT_MUTED);
        }
    }

    /** A search result's picture with its title beside it, and who made it underneath. */
    private void drawSelectedResult(GuiPort.Canvas canvas, SearchResult result, int x, int y) {
        GuiPort.Picture picture = ClientSearch.getInstance().picture(result.key());
        if (picture != null) {
            canvas.picture(picture, x, y, RESULT_PICTURE_WIDTH, RESULT_PICTURE_HEIGHT);
        } else {
            canvas.fill(x, y, x + RESULT_PICTURE_WIDTH, y + RESULT_PICTURE_HEIGHT, 0xFF000000);
        }
        int textX = x + RESULT_PICTURE_WIDTH + 4;
        int lineY = y;
        List<FormattedCharSequence> lines = canvas.font.split(Component.literal(result.title()), RIGHT_WIDTH - RESULT_PICTURE_WIDTH - 4);
        for (int i = 0; i < Math.min(lines.size(), MAX_RESULT_TITLE_LINES); i++) {
            canvas.line(lines.get(i), textX, lineY, SteelStyle.TEXT);
            lineY += canvas.font.lineHeight + 1;
        }
        int detailY = y + RESULT_PICTURE_HEIGHT + 3;
        cropped(canvas, result.uploader(), x, detailY, RIGHT_WIDTH, SteelStyle.TEXT_DIM);
        canvas.text(SteelStyle.time(result.durationMs()), x, detailY + canvas.font.lineHeight + 1, SteelStyle.TEXT_MUTED);
    }

    private static String size(long bytes) {
        if (bytes <= 0) return "? KB";
        if (bytes < 1024 * 1024) return String.format("%.0f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static boolean within(GuiPort.Control control, int x, int y) {
        return control.visible && x >= control.getX() && x < control.getX() + control.getWidth()
            && y >= control.getY() && y < control.getY() + control.getHeight();
    }

    /** Disabled controls explain themselves rather than showing their name. */
    private void drawControlTooltip(GuiPort.Canvas canvas, int mouseX, int mouseY) {
        String scope = scopeText();
        for (SteelButton button : List.of(shuffleButton, previousButton, mainButton, nextButton, repeatButton, stopButton)) {
            if (within(button, mouseX, mouseY)) {
                canvas.tooltip(!canControl() ? Component.literal(scope) : button.getMessage(), mouseX, mouseY);
                return;
            }
        }
        if (within(progressSlider, mouseX, mouseY) && !canControl()) {
            canvas.tooltip(Component.literal(scope), mouseX, mouseY);
        } else if (within(volumeSlider, mouseX, mouseY)) {
            canvas.tooltip(canTune() ? label("gui.phonon.speaker.volume_here") : Component.translatable("gui.phonon.speaker.only_owner", ownerName()), mouseX, mouseY);
        } else if (within(leaveLink, mouseX, mouseY)) {
            canvas.tooltip(label("gui.phonon.speaker.own_music_tip"), mouseX, mouseY);
        }
    }
}
