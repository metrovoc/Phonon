# Changelog

## 0.3.0

For Minecraft 26.2. This release promotes the 0.3 beta line, including stations,
playlists, portable boomboxes, in-game track search, and speaker controls.
Minecraft 1.21.1 remains on 0.3.0-beta.4; other targets retain their existing releases.

### Fixes and performance

- Fix native memory growth during extended playback on Minecraft 26.2.
- Hovering a stopped boombox no longer crashes the client.
- Private speakers save their current playback position when their chunk unloads.
- Selecting a track beyond the first 2,000 library entries plays the selected track.
- Concurrent audio transfers share bandwidth, so one track no longer waits for
  another track's entire download.
- Capacity rejections retry with bounded backoff; failed transfers leave buffering
  and show the playback error state.
- Station edits coalesce disk saves, and playback changes synchronize only the
  station view that changed.

Clients and servers must both update because the audio transfer protocol changed.

## 0.3.0-beta.4

For Minecraft 26.2 and 1.21.1. Other targets stay at `0.3.0-alpha.3`. The only change since the
tested commits is this version number and these notes.

### Finding tracks

- Find tracks by title in the game. On the Tracks tab, type a title, press Enter, and add a result
  with its **+** or a double click. Results show pictures, uploader and length.
- `/phonon add <title>` adds the first YouTube result.
- Search YouTube, SoundCloud or Bilibili from the list beside the results. Bilibili often refuses,
  so it says so and offers another try.
- The server does the searching and fetches the pictures, so players never contact the sites.

### Speaker screen

- One search box. Typing filters your library, and a green row offers to find it online or add a pasted link.
- Playlists: add songs by typing, without leaving the playlist. Drag to reorder, and rename in place.
- Add a song to playlists from the Tracks tab, with ticks showing where it already is.
- Once tuned in, the screen says who a button affects. Locked controls go grey and say why, instead of a chat error.
- Tuning in pauses your own music, and leaving brings it back where it was.
- Starting a station shares what's playing, so the music carries on.
- The main button only pauses and resumes. Songs play from their row: double-click or the play icon.
- Long names run under Now Playing, one way and wrapping around. Lists stay still and keep their tooltips.

### Permissions

- Stations answer to whoever created them and operators. `anyoneCanControlStations` opens them up.
- Deleting a track is for operators and whoever added it, even with `anyoneCanAddTracks` on.
- `anyoneCanAddTracks` now also shows the search to the players it allows.
- Redstone on a speaker tuned to a station only works if that speaker's owner created the station,
  unless `anyoneCanControlStations` is on.
- A speaker or boombox can only be tuned to a shared station, never to another speaker's own music.

### Download tools

- Failures say what happened and offer what could help, including "Update yt-dlp and try again" for operators.
- `/phonon tools` shows versions and age, and which sites last worked or were blocked. `/phonon tools help` covers blocked servers.
- Operators are told on joining if yt-dlp is over 30 days old. Updating installs Phonon's own copy if it's only on the system.
- New server settings: `downloadProxy`, `cookiesFile` and `autoUpdateYtDlp`, all off by default.

### Test evidence

- Targets: Minecraft 26.2, NeoForge 26.2.0.93, Java 25, and Minecraft 1.21.1, NeoForge 21.1.235,
  Java 21, both tested on macOS on Apple Silicon.
- Automated tests and the scripted checklist pass on both, in the real client and on a dedicated server.
- Two clients on a dedicated server, on both: locked controls and tooltips for a player who doesn't
  own a speaker or station, deleting only your own track, a boombox resuming its own music after
  leaving a station, playlist reorder and rename, and all of it after a server restart.
- Redstone on someone else's speaker left a station alone, and on the creator's own speaker paused
  and resumed it.
- Real searches on YouTube and SoundCloud returned results with pictures, and adding a YouTube result
  worked. Bilibili refused the search in-game, and showed its message and Try again.
- "Update yt-dlp and try again" installed and ran Phonon's own copy.

### Not tested

- A successful Bilibili search in the game. Tests outside the game with a login worked 5 times in 9,
  and without one 1 time in 7, on one home connection.
- A real YouTube block, and `downloadProxy` or `cookiesFile` against a real site. A bad proxy address
  did reach yt-dlp and fail as expected.
- A cloud server, Windows and Linux, and two computers over a real network.
- Very long names on the Chinese translation, and long playlist menus.

### Known issues

- A boombox in a chest or another container is silent until someone takes it out.
- In creative mode the server can't see the cursor, so a boombox held there for over 5 seconds goes
  quiet until it's put down.
- Pasted links and search results are downloaded by the server, so a site that blocks the server
  blocks both.

## 0.3.0-beta.3

The final beta of this round: the Boombox, stations, queue and playlists, and a new look
and sound for the Speaker. For Minecraft 26.2 and 1.21.1; other targets stay at
`0.3.0-alpha.3`. The only change since the tested commits is this version number,
these notes and the README.

### Boombox

- Add the Boombox, a speaker you carry. It keeps playing in your inventory, on your
  cursor and on the ground, and carries on where it was after a relog or server restart.
- Its controls are the Speaker's. Sneak and use it to switch it on or off.
- It tunes to stations, staying in step with every speaker on them.
- With `ownerOnlySpeakers`, what plays on a station is up to its creator and operators,
  whichever speaker or boombox is tuned to it.
- Tuning a speaker to a station no longer restarts the sound for everyone listening.
- Sound comes back right after changing dimension.

### Speaker screen

- Replace the grey vanilla buttons and slider with steel ones, keeping the panel.
- Add previous and next track buttons, and a Selected panel with the full track
  name, length and size.
- The main button plays the selected track, instead of pausing the current one.
- Mark the playing track in the list, and show tooltips for long names.
- Explain an empty library, a search with no results, buffering and unavailable
  tracks.
- Scroll the list with the mouse wheel.
- One Esc closes the screen even from the search box, and it closes by itself when
  you walk away or the Speaker is gone.

### Speaker block

- Show a playing state: the cone pulses and a green light stays bright in the dark.

### Sound

- Walls and water now muffle speakers instead of only making them quieter, unless
  Sound Physics Remastered is installed.
- Fade in and out on play, pause, stop and seek.
- Stop game music while a speaker is audible.
- Fix pausing a singleplayer game leaving audio permanently behind and cutting
  tracks off early: the playback clock now stops with the game.
- Restart a speaker that falls out of step with the clock, as after a network stall.

### World

- Redstone controls the speaker by side, and comparators read track progress.
- Parrots and allays dance to speakers, and sculk sensors hear them start and stop.
- Note particles and a new level meter in the screen follow the music's loudness.

### Stations and playlists

- Add a queue with repeat, shuffle, previous and next. Tracks play on by themselves.
- Add playlists, credited with their creator's name and head.
- Add named stations: tune several speakers to one and they stay in step. Existing
  speakers keep playing, on a private station of their own.
- Operators can add a link and delete tracks from the screen, with live progress.
- Add the `ownerOnlySpeakers` and `anyoneCanAddTracks` server settings.

### Also

- Show "Now Playing" when the Speaker screen closes if a track started behind it,
  since the screen hid the line for its whole 3 seconds.

### Test evidence

- Target: Minecraft 26.2, NeoForge 26.2.0.93, Java 25, and Minecraft 1.21.1, NeoForge
  21.1.235, Java 21, both tested on macOS on Apple Silicon.
- Automated tests and the scripted checklist (`neoforge/driver/checklist`) pass on both
  in the real client and on a dedicated server.
- Boombox, with two clients on a dedicated server, on both targets: the same sound carried
  on through a change of slot, the cursor, sorting into another slot, dropping and picking
  up. It switched off and on, resumed after a relog at the point it had reached, followed
  its holder through the Nether, and stayed tuned through a server restart.
- Playback clocks between the two clients stayed within 5 ms on a station and after a
  relog within 18 ms; a client joining while the track played was off by up to 114 ms until
  its next restart. This compares playback clocks, not the sound itself.
- A player without permission, tuned in by boombox with `ownerOnlySpeakers`, couldn't
  stop or skip a station, and could run the boombox's own station.

### Not tested

- Windows and Linux, and two computers over a real network.
- Multi-hour sessions, and many boomboxes on one server.
- In-game tool install, YouTube links and the music folder weren't run again this round.

### Known issues

- A boombox in a chest or another container is silent until someone takes it out.
- In creative mode the server can't see the cursor, so a boombox held there longer than
  5 seconds goes quiet until it's put down.

## 0.3.0-beta.2

First published beta, for Minecraft 26.2 and 1.21.1. Other targets stay at
`0.3.0-alpha.3`. The `0.3.0-beta.1` tags pointed at the wrong commits, so the
release checks rejected them. They produced no release and remain reserved. The
only change since the tested commit is this version number and these notes.

### Test evidence

- Target: Minecraft 1.21.1, NeoForge 21.1.235, Java 21, commit `65f0555`, tested on macOS on Apple Silicon.
- Automated tests and the scripted checklist (`neoforge/driver/checklist`) pass in
  the real client and on a dedicated server: play, pause, resume, seek, stop,
  volume, several speakers, automatic stop, resource reload, rejoin, server
  restart, and a player without operator permission.
- Sync between two clients on one machine: a late joiner's playback clock was
  within 10 ms of the first player's, and 5 ms after a seek. This compares
  playback clocks, not the sound itself.
- In-game tool install, YouTube link and music folder import pass on this target.

### Not tested

- Windows and Linux, including the tool downloads on those systems.
- Two computers over a real network, and multi-hour sessions.
- `/phonon tools update`, `allowToolInstall=false`, and non-default `audioQuality`.

### Known issues

- Pausing a singleplayer game may leave audio behind the playback clock. This is
  suspected from the code and hasn't been measured.
- On 1.21.1, resource reload can log `Stop: Invalid name parameter`. Playback
  recovers.

### Getting started

- List the Speaker in the creative menu, unlock its recipe on picking up a
  jukebox, and add a first advancement.
- Offer to install yt-dlp, FFmpeg and Deno from chat when an operator needs them,
  using pinned builds with verified checksums, then retry the add. Turn this off
  with `allowToolInstall`. Add `/phonon tools` to see what was found and update
  yt-dlp.
- Import audio files from `phonon_music` in the world folder on startup and on
  `/phonon reload`.
- `/phonon add` no longer needs a name or quotes: it can use the video's title.

### Playback

- Follow the Jukebox/Note Blocks volume slider instead of Blocks.
- Show the vanilla "Now Playing" line when a nearby speaker starts a new track.

### Messages

- Say why adding audio failed and what to do about it, instead of always blaming
  yt-dlp.
- Translate all GUI and command text, and add Simplified Chinese.

### Fixes and settings

- Save the track list on every change, so `/phonon reload` no longer forgets
  tracks added since the server started.
- Add the `audioQuality` server setting.

## 0.3.0-alpha.3

This alpha fixes bugs reported by players. It passes compilation and automated
tests on every target. Manual gameplay testing is still outstanding, so this
build remains alpha.

### Fixes

- Restore speaker and config screen text on targets where the game skips
  colours with zero alpha.
- Run yt-dlp directly instead of probing it with a 5 second limit, which could
  report a slow start as "not found" and block the server thread.
- Log yt-dlp's output when it fails, and the `PATH` when it cannot start.
- Keep the track list highlight inside the selection box on Minecraft 1.21.1
  through 1.21.8.

## 0.3.0-alpha.1

This is an alpha build. Every target passes compilation and automated tests,
and selected targets pass server or client startup smoke tests, but each
published Minecraft target still requires manual in-game validation before
promotion to beta.

### Performance

- Move OGG page indexing from whole-file reads to bounded sequential channel
  reads.
- Move server audio reads off the tick thread, keep file channels open per
  transfer, prefetch two chunks, and schedule players fairly under global and
  per-player byte budgets.
- Remove repeated packet splitting and full-buffer cache assembly from client
  streaming.
- Decode PCM directly into OpenAL-bound buffers and retain partial Vorbis frames
  without reallocating native scratch memory on every fill.
- Share compressed cached OGG bytes across speakers while keeping independent
  decoder state.
- Replace repeated collision ray clips with staggered block traversal for
  acoustic occlusion.
- Cache sorted audio metadata snapshots and normalized client search keys.

### Reliability and security

- Add explicit stream IDs, ordered chunks, cancellation, reference-counted
  shared downloads, and safe forward-only stream reuse.
- Use monotonic local playback clocks and latency-adjusted state snapshots
  instead of comparing wall clocks across machines.
- Validate speaker interaction distance, resource IDs, actions, seek bounds,
  volume, packet collection sizes, strings, headers, chunks, and sample rates.
- Validate OGG Vorbis files before registration and bound direct downloads and
  converted file sizes.
- Save metadata and cached streams through temporary files with atomic
  replacement where supported.
- Enforce the configured client cache limit and clean incomplete cache files.
- Reset server and client streaming state across disconnects, resource reloads,
  and repeated integrated-server lifecycles.

### Build and release management

- Add exact-target release branches for Minecraft 1.21.1 through 1.21.11, 26.1,
  26.1.1, 26.1.2, and 26.2; `main` targets 26.2.
- Pin each branch to its matching NeoForge release. Minecraft 1.21.x uses Java
  21, while Minecraft 26.x uses Java 25.
- Remove unused Mixin declarations and replace the optional Cloth Config screen
  with a dependency-free vanilla screen.
- Add unit tests for OGG indexing, seek lookup, segmented buffering, playback
  clocks, metadata indexes, persistence, and stream sharing.
- Add explicit alpha, beta, and release channel mapping with version-qualified
  Minecraft tags and guarded GitHub publishing environments.
- Pass dedicated-server startup smoke tests on 1.21.1, 1.21.2, 1.21.4, 1.21.9,
  26.1, and 26.2, plus a 26.2 client resource and audio-engine startup smoke
  test. The full multiplayer and playback checklist remains untested.
