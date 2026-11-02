# Phonon

A custom audio system designed to orchestrate an immersive soundscape in your game.

Phonon is currently in alpha. The default branch targets Minecraft 26.2, and
every supported Minecraft release receives its own NeoForge artifact. The
Minecraft version in the filename must match the game exactly.

## Features

- **Speaker Blocks** - Place in-world speakers that play custom audio
- **Real-time Sync** - All players hear the same audio at the same time
- **Universal URL Support** - YouTube, Bilibili, or any URL via yt-dlp integration
- **Music Folder** - Drop audio files into the world and they become tracks
- **Spatial Audio** - Sound emanates from speaker locations
- **Streaming Playback** - Playback can begin before a track finishes downloading
- **Bounded Cache** - Downloaded OGG files are cached with configurable LRU-style eviction

## Requirements

Client and server:

- Java and NeoForge versions declared by the selected Minecraft artifact

Adding audio happens on the machine that runs the server, which in singleplayer
is your own PC. Direct links to OGG files need nothing else. Other links and
non-OGG files need yt-dlp, FFmpeg and Deno.

If they are missing, Phonon offers to install them in chat the first time they
are needed. An operator clicks to accept, and pinned builds with verified
checksums go into `phonon/tools` in the game folder. Server owners can turn this
off with `allowToolInstall` in the server config, and tools already on `PATH`
are used when Phonon hasn't installed its own.

To install them yourself on Windows:

```powershell
winget install --source winget --id yt-dlp.yt-dlp
```

winget also installs FFmpeg and Deno. Restart the Minecraft Launcher afterwards.

## Usage

1. Find the Speaker in the creative menu under Functional Blocks or Redstone
   Blocks, or craft it once you have a jukebox
2. Add a song with `/phonon add <link>`
3. Right-click the Speaker, select the track and press play

All players near the speaker will hear synchronized playback.

Volume follows the game's Jukebox/Note Blocks slider.

To add local files, put them in `phonon_music` inside the world folder and run
`/phonon reload`. MP3, WAV, FLAC, M4A, AAC, Opus, WMA and OGG are accepted. The
originals stay in the folder.

## Commands

All commands need operator permission.

| Command | Description |
|---------|-------------|
| `/phonon add [name] <link>` | Download and register audio. Without a name, it uses the video's title |
| `/phonon list` | View registered audio |
| `/phonon remove <name>` | Remove audio |
| `/phonon reload` | Reload configuration and import new files from the music folder |
| `/phonon tools` | Show which audio tools were found |
| `/phonon tools install` | Install yt-dlp, FFmpeg and Deno into the game folder |
| `/phonon tools update` | Update the yt-dlp that Phonon installed |

The server config's `audioQuality` sets the Vorbis quality of newly added audio,
from 0 (smallest) to 10 (best).

## Compatibility

Each target is pinned to one Minecraft and NeoForge version. `main` tracks the
newest target (26.2); older targets live on permanent release branches.

| Minecraft | NeoForge | Java | Release branch |
| --- | --- | --- | --- |
| 1.21.1 | 21.1.235 | 21 | `release/minecraft-1.21.1` |
| 1.21.2 | 21.2.1-beta | 21 | `release/minecraft-1.21.2` |
| 1.21.3 | 21.3.96 | 21 | `release/minecraft-1.21.3` |
| 1.21.4 | 21.4.157 | 21 | `release/minecraft-1.21.4` |
| 1.21.5 | 21.5.97 | 21 | `release/minecraft-1.21.5` |
| 1.21.6 | 21.6.20-beta | 21 | `release/minecraft-1.21.6` |
| 1.21.7 | 21.7.25-beta | 21 | `release/minecraft-1.21.7` |
| 1.21.8 | 21.8.53 | 21 | `release/minecraft-1.21.8` |
| 1.21.9 | 21.9.16-beta | 21 | `release/minecraft-1.21.9` |
| 1.21.10 | 21.10.64 | 21 | `release/minecraft-1.21.10` |
| 1.21.11 | 21.11.42 | 21 | `release/minecraft-1.21.11` |
| 26.1 | 26.1.0.19-beta | 25 | `release/minecraft-26.1` |
| 26.1.1 | 26.1.1.15-beta | 25 | `release/minecraft-26.1.1` |
| 26.1.2 | 26.1.2.80 | 25 | `release/minecraft-26.1.2` |
| 26.2 | 26.2.0.15-beta | 25 | `release/minecraft-26.2` |

All targets pass compilation and the automated test suite. Dedicated-server
startup has additionally been exercised on 1.21.1, 1.21.2, 1.21.4, 1.21.9,
26.1, and 26.2; 26.2 also passes a client startup and resource/audio-engine
smoke test. No target has completed the full manual gameplay checklist yet, so
all of them remain alpha.

## Releases

Release tags use `v<mod-version>+mc<minecraft-version>`, for example
`v0.3.0-alpha.2+mc26.2`. Untested ports remain alpha, manually smoke-tested
builds may become beta, and only soaked beta builds become stable. See
[RELEASING.md](RELEASING.md) for the branch model, promotion rules, and test
checklist.
