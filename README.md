# Phonon

A custom audio system designed to orchestrate an immersive soundscape in your game.

Phonon is currently in alpha. The default branch targets Minecraft 26.2, and
every supported Minecraft release receives its own NeoForge artifact. The
Minecraft version in the filename must match the game exactly.

## Features

- **Speaker Blocks** - Place in-world speakers that play custom audio
- **Real-time Sync** - All players hear the same audio at the same time
- **Universal URL Support** - YouTube, Bilibili, or any URL via yt-dlp integration
- **Spatial Audio** - Sound emanates from speaker locations
- **Streaming Playback** - Playback can begin before a track finishes downloading
- **Bounded Cache** - Downloaded OGG files are cached with configurable LRU-style eviction

## Requirements

Client and server:

- Java and NeoForge versions declared by the selected Minecraft artifact

Server-side (for audio downloading):

- [FFmpeg](https://ffmpeg.org/)
- [yt-dlp](https://github.com/yt-dlp/yt-dlp) (optional, for URL support)

Direct OGG URLs work without yt-dlp. Other media sites require yt-dlp and its
FFmpeg integration.

## Commands

| Command | Description |
|---------|-------------|
| `/phonon add <name> <url>` | Download and register audio |
| `/phonon list` | View registered audio |
| `/phonon remove <name>` | Remove audio |
| `/phonon reload` | Reload configuration |

## Usage

1. Place a Speaker block
2. Right-click to open the control GUI
3. Select a track and press play

All players near the speaker will hear synchronized playback.

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
